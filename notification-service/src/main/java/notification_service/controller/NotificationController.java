package notification_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import notification_service.model.Notification;
import notification_service.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(notificationRepository.findByUserId(user.userId()));
    }
}