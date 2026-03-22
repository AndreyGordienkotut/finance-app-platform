package notification_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.model.Channel;
import notification_service.model.Notification;
import notification_service.model.NotificationStatus;
import notification_service.repository.NotificationRepository;
import notification_service.repository.UserTelegramRepository;
import org.springframework.stereotype.Service;
import core.core.dto.TransactionKafkaEvent;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TelegramService telegramService;
    private final UserTelegramRepository userTelegramRepository;

    public void processTransactionNotification(TransactionKafkaEvent event) {
        log.info("Processing notification for TX {} user {}",
                event.getTransactionId(), event.getUserId());

        String message = buildMessage(event);

        Notification notification = Notification.builder()
                .userId(event.getUserId())
                .channel(Channel.IN_APP)
                .message(message)
                .subject("Transaction completed")
                .transactionId(event.getTransactionId())
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        notification = notificationRepository.save(notification);

        try {
            sendTelegramIfLinked(event.getUserId(), message);
            log.info("Notification sent for TX {} user {}: {}",
                    event.getTransactionId(), event.getUserId(), message);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send notification for TX {}: {}",
                    event.getTransactionId(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        } finally {
            notificationRepository.save(notification);
        }
    }
    private void sendTelegramIfLinked(Long userId, String message) {
        userTelegramRepository.findByUserId(userId).ifPresentOrElse(
                userTelegram -> telegramService.sendMessage(userTelegram.getChatId(), message),
                () -> log.info("No Telegram linked for user {}", userId)
        );
    }
    private String buildMessage(TransactionKafkaEvent event) {
        return String.format(
                "Transaction %s completed. Amount: %s %s. Type: %s",
                event.getTransactionId(),
                event.getTargetAmount(),
                event.getCurrency(),
                event.getTransactionType()
        );
    }
}
