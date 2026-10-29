package com.flowdesk.notification;

import com.flowdesk.serviceorder.ServiceOrderEvents;
import com.flowdesk.user.Role;
import com.flowdesk.user.User;
import com.flowdesk.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into in-app notifications and e-mails. Runs on another thread and only after the
 * originating transaction committed, so notification problems can never affect the business operation.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notifications;
    private final UserRepository users;
    private final EmailGateway email;

    public NotificationListener(NotificationService notifications, UserRepository users, EmailGateway email) {
        this.notifications = notifications;
        this.users = users;
        this.email = email;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssigned(ServiceOrderEvents.Assigned e) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (!e.assigneeId().equals(e.actorId())) {
            recipients.add(e.assigneeId());
        }
        deliver(e.companyId(), recipients, NotificationType.ORDER_ASSIGNED,
                e.code() + " was assigned to you: " + e.title(), e.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(ServiceOrderEvents.StatusChanged e) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (e.assigneeId() != null) {
            recipients.add(e.assigneeId());
        }
        recipients.add(e.createdById());
        recipients.remove(e.actorId()); // do not notify people about their own action
        deliver(e.companyId(), recipients, NotificationType.STATUS_CHANGED,
                e.code() + " moved from " + e.from() + " to " + e.to(), e.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSlaAlert(ServiceOrderEvents.SlaAlert e) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (e.assigneeId() != null) {
            recipients.add(e.assigneeId());
        }
        users.findByCompanyIdAndRoleInAndActiveTrue(e.companyId(), List.of(Role.ADMIN, Role.MANAGER))
                .forEach(u -> recipients.add(u.getId()));

        boolean breached = e.type() == ServiceOrderEvents.SlaAlertType.BREACHED;
        deliver(e.companyId(), recipients,
                breached ? NotificationType.SLA_BREACHED : NotificationType.SLA_DUE_SOON,
                e.code() + (breached ? " breached its SLA: " : " is due soon: ") + e.title(),
                e.orderId());
    }

    private void deliver(UUID companyId, Set<UUID> recipientIds, NotificationType type, String message, UUID orderId) {
        if (recipientIds.isEmpty()) {
            return;
        }
        try {
            notifications.create(companyId, recipientIds, type, message, orderId);
            List<User> recipients = users.findByCompanyIdAndIdIn(companyId, recipientIds);
            recipients.forEach(u -> email.send(u.getEmail(), "[FlowDesk] " + type, message));
        } catch (RuntimeException ex) {
            // Never propagate: the business transaction already committed.
            log.error("Failed to deliver {} notification for order {}", type, orderId, ex);
        }
    }
}
