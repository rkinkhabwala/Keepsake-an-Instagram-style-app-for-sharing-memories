package com.keepsake.notification.controller;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.notification.dto.NotificationDto;
import com.keepsake.notification.dto.UnreadCountDto;
import com.keepsake.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public CursorPage<NotificationDto> list(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return notifications.list(viewerId, cursor, limit);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal UUID viewerId) {
        return new UnreadCountDto(notifications.unreadCount(viewerId));
    }

    /** Marks every notification read. */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal UUID viewerId) {
        notifications.markAllRead(viewerId);
    }
}
