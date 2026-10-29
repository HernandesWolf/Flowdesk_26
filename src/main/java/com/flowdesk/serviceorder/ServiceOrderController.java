package com.flowdesk.serviceorder;

import com.flowdesk.audit.AuditService;
import com.flowdesk.common.web.PageRequests;
import com.flowdesk.common.web.PageResponse;
import com.flowdesk.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-orders")
@Tag(name = "Service orders")
public class ServiceOrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "dueAt", "number", "priority", "status", "updatedAt");

    private final ServiceOrderService service;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public ServiceOrderController(ServiceOrderService service, AuditService auditService, CurrentUser currentUser) {
        this.service = service;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceOrderDtos.Response create(@Valid @RequestBody ServiceOrderDtos.CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<ServiceOrderDtos.Response> search(
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        var criteria = new ServiceOrderService.SearchCriteria(status, priority, assigneeId, customerId, overdue, q);
        return service.search(criteria, PageRequests.of(page, size, sortBy, direction, SORTABLE));
    }

    @GetMapping("/{id}")
    public ServiceOrderDtos.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceOrderDtos.Response update(
            @PathVariable UUID id, @Valid @RequestBody ServiceOrderDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ServiceOrderDtos.Response assign(
            @PathVariable UUID id, @Valid @RequestBody ServiceOrderDtos.AssignRequest request) {
        return service.assign(id, request.technicianId());
    }

    @PostMapping("/{id}/transitions")
    public ServiceOrderDtos.Response transition(
            @PathVariable UUID id, @Valid @RequestBody ServiceOrderDtos.TransitionRequest request) {
        return service.transition(id, request);
    }

    @GetMapping("/{id}/allowed-transitions")
    public List<ServiceOrderStatus> allowedTransitions(@PathVariable UUID id) {
        return service.allowedTransitions(id);
    }

    @GetMapping("/{id}/timeline")
    public PageResponse<AuditService.Entry> timeline(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        service.requireVisible(id); // enforces tenant + visibility rules before touching the audit trail
        return PageResponse.map(
                auditService.timeline(currentUser.companyId(), id, page, size), AuditService.Entry::from);
    }
}
