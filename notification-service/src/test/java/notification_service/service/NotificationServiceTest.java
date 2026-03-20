package notification_service.service;

import core.core.dto.TransactionKafkaEvent;
import notification_service.model.Channel;
import notification_service.model.Notification;
import notification_service.model.NotificationStatus;
import notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import core.core.exception.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private TransactionKafkaEvent buildEvent() {
        return TransactionKafkaEvent.builder()
                .transactionId(1L)
                .userId(10L)
                .amount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("90.00"))
                .currency("USD")
                .transactionType("TRANSFER")
                .categoryName("ENTERTAINMENT")
                .createdAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(notificationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("processTransactionNotification() - saves PENDING then SENT")
    void process_savesPendingThenSent() {
        TransactionKafkaEvent event = buildEvent();

        List<NotificationStatus> savedStatuses = new ArrayList<>();
        when(notificationRepository.save(any())).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            savedStatuses.add(n.getStatus());
            return n;
        });

        notificationService.processTransactionNotification(event);

        assertEquals(2, savedStatuses.size());
        assertEquals(NotificationStatus.PENDING, savedStatuses.get(0));
        assertEquals(NotificationStatus.SENT, savedStatuses.get(1));
    }

    @Test
    @DisplayName("processTransactionNotification() - sets correct userId and transactionId")
    void process_setsCorrectUserIdAndTransactionId() {
        TransactionKafkaEvent event = buildEvent();

        notificationService.processTransactionNotification(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Notification first = captor.getAllValues().get(0);
        assertEquals(10L, first.getUserId());
        assertEquals(1L, first.getTransactionId());
        assertEquals(Channel.IN_APP, first.getChannel());
        assertEquals("Transaction completed", first.getSubject());
    }

    @Test
    @DisplayName("processTransactionNotification() - sets correct createdAt")
    void process_setsCreatedAt() {
        TransactionKafkaEvent event = buildEvent();

        notificationService.processTransactionNotification(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        assertNotNull(captor.getAllValues().get(0).getCreatedAt());
    }

    @Test
    @DisplayName("processTransactionNotification() - message contains transaction data")
    void process_messageContainsTransactionData() {
        TransactionKafkaEvent event = buildEvent();

        notificationService.processTransactionNotification(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        String message = captor.getAllValues().get(0).getMessage();
        assertTrue(message.contains("1"));
        assertTrue(message.contains("90.00"));
        assertTrue(message.contains("USD"));
        assertTrue(message.contains("TRANSFER"));
    }

    @Test
    @DisplayName("processTransactionNotification() - repository called twice (save + update)")
    void process_repositoryCalledTwice() {
        TransactionKafkaEvent event = buildEvent();

        notificationService.processTransactionNotification(event);

        verify(notificationRepository, times(2)).save(any());
    }
}