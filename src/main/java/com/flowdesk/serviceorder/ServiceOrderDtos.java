package com.flowdesk.serviceorder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServiceOrderDtos {

    private ServiceOrderDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull UUID customerId,
            @NotNull Priority priority,
            UUID assigneeId) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull Priority priority) {}

    public record AssignRequest(@NotNull UUID technicianId) {}

    /**
     * Moves an order through the workflow. The optional text fields are persisted with the transition so a
     * technician can submit "diagnosis + move to execution" in one call.
     */
    public record TransitionRequest(
            @NotNull ServiceOrderStatus targetStatus,
            @Size(max = 5000) String diagnosis,
            @Size(max = 5000) String resolution,
            @Size(max = 1000) String reason) {}

    public record PartyRef(UUID id, String name) {}

    public record Response(
            UUID id,
            long number,
            String code,
            String title,
            String description,
            ServiceOrderStatus status,
            Priority priority,
            PartyRef customer,
            PartyRef assignee,
            PartyRef createdBy,
            String diagnosis,
            String resolution,
            String cancellationReason,
            Instant dueAt,
            boolean overdue,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            List<ServiceOrderStatus> availableTransitions) {

        public static Response from(ServiceOrder o, Instant now, List<ServiceOrderStatus> availableTransitions) {
            return new Response(
                    o.getId(),
                    o.getNumber(),
                    o.getCode(),
                    o.getTitle(),
                    o.getDescription(),
                    o.getStatus(),
                    o.getPriority(),
                    new PartyRef(o.getCustomer().getId(), o.getCustomer().getName()),
                    o.getAssignee() == null ? null : new PartyRef(o.getAssignee().getId(), o.getAssignee().getName()),
                    new PartyRef(o.getCreatedBy().getId(), o.getCreatedBy().getName()),
                    o.getDiagnosis(),
                    o.getResolution(),
                    o.getCancellationReason(),
                    o.getDueAt(),
                    o.isOverdue(now),
                    o.getCreatedAt(),
                    o.getUpdatedAt(),
                    o.getCompletedAt(),
                    availableTransitions);
        }
    }
}
