package notification_service.kafka;

import core.core.dto.TransactionKafkaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionNotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "transaction.completed",
            groupId = "notification-service"
    )
    public void consume(TransactionKafkaEvent event) {
        log.info("Received Kafka event for TX {} user {}",
                event.getTransactionId(), event.getUserId());
        try {
            notificationService.processTransactionNotification(event);
        } catch (Exception e) {
            log.error("Failed to process notification for TX {}: {}",
                    event.getTransactionId(), e.getMessage());
        }
    }
}