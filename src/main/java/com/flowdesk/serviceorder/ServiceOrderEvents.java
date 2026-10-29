package com.flowdesk.serviceorder;

import java.util.UUID;

/**
 * Domain events published inside the business transaction. Listeners (notifications) run asynchronously
 * after commit, so a slow mail server can never slow down or roll back a state change.
 */
public final class ServiceOrderEvents {

    private ServiceOrderEvents() {}

    public record Assigned(
            UUID companyId, UUID orderId, String code, String title, UUID assigneeId, UUID actorId) {}

    public record StatusChanged(
            UUID companyId,
            UUID orderId,
            String code,
            String title,
            ServiceOrderStatus from,
            ServiceOrderStatus to,
            UUID assigneeId,
            UUID createdById,
            UUID actorId) {}

    public enum SlaAlertType {
        DUE_SOON,
        BREACHED
    }

    public record SlaAlert(
            UUID companyId, UUID orderId, String code, String title, UUID assigneeId, SlaAlertType type) {}
}
