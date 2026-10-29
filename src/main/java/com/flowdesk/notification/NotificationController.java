package com.flowdesk.notification;

import com.flowdesk.common.web.PageRequests;
import com.flowdesk.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private static final Set<String> SORTABLE = Set.of("createdAt");

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationDtos.Response> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(unreadOnly, PageRequests.of(page, size, "createdAt", "desc", SORTABLE));
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCount unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/{id}/read")
    public NotificationDtos.Response markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead());
    }
}
