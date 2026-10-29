package com.flowdesk.dashboard;

import com.flowdesk.security.CurrentUser;
import com.flowdesk.serviceorder.Priority;
import com.flowdesk.serviceorder.ServiceOrderRepository;
import com.flowdesk.serviceorder.ServiceOrderStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ServiceOrderRepository orders;
    private final CurrentUser currentUser;

    public DashboardService(ServiceOrderRepository orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    public DashboardDtos.Summary summary(int days) {
        int period = Math.min(Math.max(days, 1), 365);
        UUID companyId = currentUser.companyId();
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(period));
        var finished = ServiceOrderStatus.finishedStatuses();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ServiceOrderStatus s : ServiceOrderStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        long total = 0;
        for (ServiceOrderRepository.StatusCount sc : orders.countByStatus(companyId)) {
            byStatus.put(sc.getStatus().name(), sc.getTotal());
            total += sc.getTotal();
        }
        long active = total - byStatus.get(ServiceOrderStatus.COMPLETED.name()) - byStatus.get(ServiceOrderStatus.CANCELLED.name());

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.name(), 0L);
        }
        for (ServiceOrderRepository.PriorityCount pc : orders.countActiveByPriority(companyId, finished)) {
            byPriority.put(pc.getPriority().name(), pc.getTotal());
        }

        long overdue = orders.countOverdue(companyId, now, finished);
        long completed = orders.countCompletedSince(companyId, ServiceOrderStatus.COMPLETED, from);
        long onTime = orders.countCompletedOnTimeSince(companyId, ServiceOrderStatus.COMPLETED, from);
        Double compliance = completed == 0 ? null : round1(100.0 * onTime / completed);
        Double avgSeconds = orders.averageResolutionSeconds(companyId, from);
        Double avgHours = avgSeconds == null ? null : round1(avgSeconds / 3600.0);

        return new DashboardDtos.Summary(
                period, total, active, overdue, completed, compliance, avgHours, byStatus, byPriority);
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
