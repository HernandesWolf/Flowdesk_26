package com.flowdesk.notification;

import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.common.web.PageResponse;
import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.security.CurrentUser;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    /** Called from the async event listener (no authenticated user there), so it takes explicit ids. */
    @Transactional
    public void create(
            UUID companyId, Collection<UUID> recipients, NotificationType type, String message, UUID serviceOrderId) {
        for (UUID recipient : recipients) {
            notifications.save(new Notification(companyId, recipient, type, message, serviceOrderId));
        }
    }

    public PageResponse<NotificationDtos.Response> list(boolean unreadOnly, Pageable pageable) {
        AuthenticatedUser actor = currentUser.get();
        Page<Notification> page = unreadOnly
                ? notifications.findByRecipientIdAndCompanyIdAndReadFalse(actor.id(), actor.companyId(), pageable)
                : notifications.findByRecipientIdAndCompanyId(actor.id(), actor.companyId(), pageable);
        return PageResponse.map(page, NotificationDtos.Response::from);
    }

    public NotificationDtos.UnreadCount unreadCount() {
        AuthenticatedUser actor = currentUser.get();
        return new NotificationDtos.UnreadCount(
                notifications.countByRecipientIdAndCompanyIdAndReadFalse(actor.id(), actor.companyId()));
    }

    @Transactional
    public NotificationDtos.Response markRead(UUID id) {
        AuthenticatedUser actor = currentUser.get();
        Notification n = notifications.findByIdAndRecipientIdAndCompanyId(id, actor.id(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.markRead();
        return NotificationDtos.Response.from(n);
    }

    @Transactional
    public int markAllRead() {
        AuthenticatedUser actor = currentUser.get();
        return notifications.markAllRead(actor.id(), actor.companyId());
    }
}
