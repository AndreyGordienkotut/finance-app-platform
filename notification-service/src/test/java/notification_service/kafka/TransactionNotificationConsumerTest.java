package notification_service.kafka;

import core.core.dto.TransactionKafkaEvent;

import notification_service.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import core.core.exception.*;

import java.math.BigDecimal;
import java.time.Instant;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionNotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TransactionNotificationConsumer consumer;

    private TransactionKafkaEvent buildEvent() {
        return TransactionKafkaEvent.builder()
                .transactionId(1L)
                .userId(10L)
                .amount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("90.00"))
                .currency("USD")
                .transactionType("TRANSFER")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("consume() - calls notificationService")
    void consume_callsNotificationService() {
        TransactionKafkaEvent event = buildEvent();

        consumer.consume(event);
        verify(notificationService).processTransactionNotification(event);
    }

    @Test
    @DisplayName("consume() - exception in service - does not propagate")
    void consume_serviceThrows_doesNotPropagate() {
        TransactionKafkaEvent event = buildEvent();

        doThrow(new RuntimeException("DB error"))
                .when(notificationService)
                .processTransactionNotification(any());

        assertDoesNotThrow(() -> consumer.consume(event));
    }

    @Test
    @DisplayName("consume() - service called once per event")
    void consume_calledOncePerEvent() {
        TransactionKafkaEvent event = buildEvent();

        consumer.consume(event);

        verify(notificationService, times(1))
                .processTransactionNotification(any());
    }
}