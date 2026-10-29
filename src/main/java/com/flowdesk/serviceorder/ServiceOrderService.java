package com.flowdesk.serviceorder;

import com.flowdesk.audit.AuditAction;
import com.flowdesk.audit.AuditService;
import com.flowdesk.common.exception.BusinessRuleException;
import com.flowdesk.common.exception.ForbiddenOperationException;
import com.flowdesk.common.exception.InvalidTransitionException;
import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.common.web.PageResponse;
import com.flowdesk.company.Company;
import com.flowdesk.company.CompanyRepository;
import com.flowdesk.customer.Customer;
import com.flowdesk.customer.CustomerRepository;
import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.security.CurrentUser;
import com.flowdesk.serviceorder.ServiceOrderWorkflow.TransitionCheck;
import com.flowdesk.serviceorder.ServiceOrderWorkflow.TransitionContext;
import com.flowdesk.user.Role;
import com.flowdesk.user.User;
import com.flowdesk.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for work orders. Responsibilities: tenant scoping, visibility by role, calling the
 * workflow for validation, writing the audit trail and publishing domain events.
 */
@Service
@Transactional(readOnly = true)
public class ServiceOrderService {

    /** Optional filters for {@link #search}. */
    public record SearchCriteria(
            ServiceOrderStatus status,
            Priority priority,
            UUID assigneeId,
            UUID customerId,
            Boolean overdue,
            String text) {}

    private final ServiceOrderRepository orders;
    private final CompanyRepository companies;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final SlaCalculator sla;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    public ServiceOrderService(
            ServiceOrderRepository orders,
            CompanyRepository companies,
            CustomerRepository customers,
            UserRepository users,
            SlaCalculator sla,
            AuditService audit,
            CurrentUser currentUser,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.companies = companies;
        this.customers = customers;
        this.users = users;
        this.sla = sla;
        this.audit = audit;
        this.currentUser = currentUser;
        this.events = events;
    }

    // ---- Commands ---------------------------------------------------------------------------------

    @Transactional
    public ServiceOrderDtos.Response create(ServiceOrderDtos.CreateRequest request) {
        AuthenticatedUser actor = currentUser.get();
        Customer customer = customers.findByIdAndCompanyId(request.customerId(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("Customer not found"));
        User creator = users.findByIdAndCompanyId(actor.id(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        User assignee = request.assigneeId() == null ? null : requireTechnician(request.assigneeId(), actor.companyId());

        Company company = companies.findByIdForUpdate(actor.companyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        long number = company.takeNextOrderNumber();

        Instant now = Instant.now();
        ServiceOrder order = new ServiceOrder(
                actor.companyId(),
                number,
                request.title().trim(),
                blankToNull(request.description()),
                customer,
                creator,
                request.priority(),
                sla.dueAt(request.priority(), now));
        if (assignee != null) {
            order.assignTo(assignee);
        }
        order = orders.save(order);

        audit.record(order, AuditAction.CREATED, null, null, order.getCode());
        if (assignee != null) {
            audit.record(order, AuditAction.ASSIGNED, "assignee", null, assignee.getName());
            publishAssigned(order, assignee, actor);
        }
        return toResponse(order, actor);
    }

    @Transactional
    public ServiceOrderDtos.Response update(UUID id, ServiceOrderDtos.UpdateRequest request) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = requireVisible(id);
        requireNotFinished(order);

        String newTitle = request.title().trim();
        String newDescription = blankToNull(request.description());
        if (!newTitle.equals(order.getTitle())) {
            audit.record(order, AuditAction.UPDATED, "title", order.getTitle(), newTitle);
        }
        if (!java.util.Objects.equals(newDescription, order.getDescription())) {
            audit.record(order, AuditAction.UPDATED, "description", order.getDescription(), newDescription);
        }
        order.updateDetails(newTitle, newDescription);

        if (request.priority() != order.getPriority()) {
            audit.record(order, AuditAction.PRIORITY_CHANGED, "priority",
                    order.getPriority().name(), request.priority().name());
            // The SLA clock always runs from creation, so a priority change moves the due date accordingly.
            order.changePriority(request.priority(), sla.dueAt(request.priority(), order.getCreatedAt()));
        }
        return toResponse(order, actor);
    }

    @Transactional
    public ServiceOrderDtos.Response assign(UUID id, UUID technicianId) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = requireVisible(id);
        requireNotFinished(order);
        User technician = requireTechnician(technicianId, actor.companyId());

        User previous = order.getAssignee();
        if (previous != null && previous.getId().equals(technician.getId())) {
            return toResponse(order, actor);
        }
        order.assignTo(technician);
        audit.record(order, AuditAction.ASSIGNED, "assignee",
                previous == null ? null : previous.getName(), technician.getName());
        publishAssigned(order, technician, actor);
        return toResponse(order, actor);
    }

    @Transactional
    public ServiceOrderDtos.Response transition(UUID id, ServiceOrderDtos.TransitionRequest request) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = requireVisible(id);
        ServiceOrderStatus from = order.getStatus();
        ServiceOrderStatus to = request.targetStatus();

        String diagnosis = firstNonBlank(request.diagnosis(), order.getDiagnosis());
        String resolution = firstNonBlank(request.resolution(), order.getResolution());
        String reason = firstNonBlank(request.reason(), order.getCancellationReason());

        TransitionCheck check = ServiceOrderWorkflow.validate(new TransitionContext(
                from, to, actor.role(), isAssignee(order, actor), order.getAssignee() != null,
                diagnosis, resolution, reason));
        switch (check.outcome()) {
            case NOT_ALLOWED -> throw new InvalidTransitionException(check.message());
            case FORBIDDEN -> throw new ForbiddenOperationException(check.message());
            case RULE_VIOLATION -> throw new BusinessRuleException(check.message());
            case OK -> { /* proceed */ }
        }

        if (!isBlank(request.diagnosis()) && !request.diagnosis().equals(order.getDiagnosis())) {
            audit.record(order, AuditAction.DIAGNOSIS_UPDATED, "diagnosis", order.getDiagnosis(), request.diagnosis());
            order.setDiagnosis(request.diagnosis());
        }
        if (!isBlank(request.resolution()) && !request.resolution().equals(order.getResolution())) {
            audit.record(order, AuditAction.RESOLUTION_UPDATED, "resolution", order.getResolution(), request.resolution());
            order.setResolution(request.resolution());
        }
        if (to == ServiceOrderStatus.CANCELLED) {
            order.setCancellationReason(reason);
        }

        order.applyStatus(to, Instant.now());
        audit.record(order, AuditAction.STATUS_CHANGED, "status", from.name(), to.name());
        events.publishEvent(new ServiceOrderEvents.StatusChanged(
                order.getCompanyId(), order.getId(), order.getCode(), order.getTitle(), from, to,
                order.getAssignee() == null ? null : order.getAssignee().getId(),
                order.getCreatedBy().getId(), actor.id()));
        return toResponse(order, actor);
    }

    // ---- Queries ----------------------------------------------------------------------------------

    public ServiceOrderDtos.Response get(UUID id) {
        AuthenticatedUser actor = currentUser.get();
        return toResponse(requireVisible(id), actor);
    }

    public List<ServiceOrderStatus> allowedTransitions(UUID id) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = requireVisible(id);
        return ServiceOrderWorkflow.availableFor(order.getStatus(), actor.role(), isAssignee(order, actor));
    }

    public PageResponse<ServiceOrderDtos.Response> search(SearchCriteria criteria, Pageable pageable) {
        AuthenticatedUser actor = currentUser.get();
        Instant now = Instant.now();

        Specification<ServiceOrder> spec = ServiceOrderSpecifications.forCompany(actor.companyId());
        if (actor.role() == Role.TECHNICIAN) {
            // Technicians only ever see their own orders, whatever filters they send.
            spec = spec.and(ServiceOrderSpecifications.assignedTo(actor.id()));
        } else if (criteria.assigneeId() != null) {
            spec = spec.and(ServiceOrderSpecifications.assignedTo(criteria.assigneeId()));
        }
        if (criteria.status() != null) {
            spec = spec.and(ServiceOrderSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.priority() != null) {
            spec = spec.and(ServiceOrderSpecifications.hasPriority(criteria.priority()));
        }
        if (criteria.customerId() != null) {
            spec = spec.and(ServiceOrderSpecifications.forCustomer(criteria.customerId()));
        }
        if (Boolean.TRUE.equals(criteria.overdue())) {
            spec = spec.and(ServiceOrderSpecifications.overdue(now));
        }
        if (criteria.text() != null && !criteria.text().isBlank()) {
            spec = spec.and(ServiceOrderSpecifications.matchesText(criteria.text()));
        }

        Page<ServiceOrder> page = orders.findAll(spec, pageable);
        return PageResponse.map(page, o -> ServiceOrderDtos.Response.from(
                o, now, ServiceOrderWorkflow.availableFor(o.getStatus(), actor.role(), isAssignee(o, actor))));
    }

    /**
     * Loads an order the caller is allowed to see: same company, and for technicians only their own.
     * Anything else is reported as "not found" so the existence of other tenants' data is never revealed.
     */
    public ServiceOrder requireVisible(UUID id) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = orders.findByIdAndCompanyId(id, actor.companyId())
                .orElseThrow(() -> new NotFoundException("Service order not found"));
        if (actor.role() == Role.TECHNICIAN && !isAssignee(order, actor)) {
            throw new NotFoundException("Service order not found");
        }
        return order;
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private ServiceOrderDtos.Response toResponse(ServiceOrder order, AuthenticatedUser actor) {
        return ServiceOrderDtos.Response.from(
                order,
                Instant.now(),
                ServiceOrderWorkflow.availableFor(order.getStatus(), actor.role(), isAssignee(order, actor)));
    }

    private User requireTechnician(UUID userId, UUID companyId) {
        return users.findByIdAndCompanyId(userId, companyId)
                .filter(User::isActive)
                .filter(u -> u.getRole() == Role.TECHNICIAN)
                .orElseThrow(() -> new BusinessRuleException("The assignee must be an active technician of your company"));
    }

    private void requireNotFinished(ServiceOrder order) {
        if (order.getStatus().isFinished()) {
            throw new BusinessRuleException("A " + order.getStatus() + " service order can no longer be changed");
        }
    }

    private void publishAssigned(ServiceOrder order, User technician, AuthenticatedUser actor) {
        events.publishEvent(new ServiceOrderEvents.Assigned(
                order.getCompanyId(), order.getId(), order.getCode(), order.getTitle(), technician.getId(), actor.id()));
    }

    private static boolean isAssignee(ServiceOrder order, AuthenticatedUser actor) {
        return order.getAssignee() != null && order.getAssignee().getId().equals(actor.id());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
