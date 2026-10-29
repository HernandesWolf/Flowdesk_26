package com.flowdesk.dashboard;

import com.flowdesk.common.exception.BadRequestException;
import com.flowdesk.security.CurrentUser;
import com.flowdesk.serviceorder.ServiceOrder;
import com.flowdesk.serviceorder.ServiceOrderRepository;
import com.flowdesk.serviceorder.ServiceOrderSpecifications;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_CSV_ROWS = 10_000;

    private final ServiceOrderRepository orders;
    private final CurrentUser currentUser;

    public ReportService(ServiceOrderRepository orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    /** Completed orders per technician in the range (both dates inclusive, UTC). */
    public List<DashboardDtos.TechnicianProductivity> productivity(LocalDate from, LocalDate to) {
        Instant[] range = range(from, to);
        List<Object[]> rows = orders.productivityByTechnician(currentUser.companyId(), range[0], range[1]);
        List<DashboardDtos.TechnicianProductivity> result = new ArrayList<>();
        for (Object[] row : rows) {
            UUID id = UUID.fromString(String.valueOf(row[0]));
            String name = String.valueOf(row[1]);
            long completed = ((Number) row[2]).longValue();
            Double avgHours = row[3] == null ? null : DashboardService.round1(((Number) row[3]).doubleValue() / 3600.0);
            long onTime = row[4] == null ? 0 : ((Number) row[4]).longValue();
            Double onTimePercent = completed == 0 ? null : DashboardService.round1(100.0 * onTime / completed);
            result.add(new DashboardDtos.TechnicianProductivity(id, name, completed, onTime, onTimePercent, avgHours));
        }
        return result;
    }

    /** Orders created in the range as CSV (capped at 10,000 rows). */
    public String serviceOrdersCsv(LocalDate from, LocalDate to) {
        Instant[] range = range(from, to);
        Specification<ServiceOrder> spec = ServiceOrderSpecifications.forCompany(currentUser.companyId())
                .and(ServiceOrderSpecifications.createdBetween(range[0], range[1]));
        List<ServiceOrder> found =
                orders.findAll(spec, PageRequest.of(0, MAX_CSV_ROWS, Sort.by("createdAt"))).getContent();

        Instant now = Instant.now();
        CsvWriter csv = new CsvWriter().row(
                "code", "title", "customer", "assignee", "priority", "status",
                "created_at", "due_at", "completed_at", "overdue", "resolution_hours");
        for (ServiceOrder o : found) {
            Double resolutionHours = o.getCompletedAt() == null
                    ? null
                    : DashboardService.round1(Duration.between(o.getCreatedAt(), o.getCompletedAt()).toSeconds() / 3600.0);
            csv.row(
                    o.getCode(), o.getTitle(), o.getCustomer().getName(),
                    o.getAssignee() == null ? null : o.getAssignee().getName(),
                    o.getPriority(), o.getStatus(), o.getCreatedAt(), o.getDueAt(), o.getCompletedAt(),
                    o.isOverdue(now), resolutionHours);
        }
        return csv.toString();
    }

    private static Instant[] range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both 'from' and 'to' (yyyy-MM-dd) are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("The range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
        return new Instant[] {
            from.atStartOfDay().toInstant(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        };
    }
}
