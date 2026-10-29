package com.flowdesk.notification;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {}

    public record Response(
            UUID id, NotificationType type, String message, UUID serviceOrderId, boolean read, Instant createdAt) {

        public static Response from(Notification n) {
            return new Response(n.getId(), n.getType(), n.getMessage(), n.getServiceOrderId(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {}
}
