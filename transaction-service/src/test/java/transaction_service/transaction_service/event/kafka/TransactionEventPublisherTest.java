package transaction_service.transaction_service.event.kafka;

import core.core.dto.TransactionKafkaEvent;
import core.core.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import core.core.exception.*;
import org.springframework.kafka.support.SendResult;
import transaction_service.transaction_service.model.*;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, TransactionKafkaEvent> kafkaTemplate;

    @InjectMocks
    private TransactionEventPublisher transactionEventPublisher;

    private Transaction buildTx(TransactionCategory category) {
        return Transaction.builder()
                .id(1L)
                .userId(10L)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("90.00"))
                .exchangeRate(new BigDecimal("0.9"))
                .currency(Currency.USD)
                .transactionType(TransactionType.TRANSFER)
                .category(category)
                .createdAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        CompletableFuture<SendResult<String, TransactionKafkaEvent>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);
    }

    @Test
    @DisplayName("publish() - sends to correct topic")
    void publish_sendsToCorrectTopic() {
        Transaction tx = buildTx(null);

        transactionEventPublisher.publish(tx);

        verify(kafkaTemplate).send(
                eq("transaction.completed"),
                eq("1"),
                any(TransactionKafkaEvent.class)
        );
    }

    @Test
    @DisplayName("publish() - event contains correct transaction data")
    void publish_eventContainsCorrectData() {
        Transaction tx = buildTx(null);

        ArgumentCaptor<TransactionKafkaEvent> captor =
                ArgumentCaptor.forClass(TransactionKafkaEvent.class);

        transactionEventPublisher.publish(tx);

        verify(kafkaTemplate).send(any(), any(), captor.capture());

        TransactionKafkaEvent event = captor.getValue();
        assertEquals(1L, event.getTransactionId());
        assertEquals(10L, event.getUserId());
        assertEquals(1L, event.getSourceAccountId());
        assertEquals(2L, event.getTargetAccountId());
        assertEquals(new BigDecimal("100.00"), event.getAmount());
        assertEquals(new BigDecimal("90.00"), event.getTargetAmount());
        assertEquals("USD", event.getCurrency());
        assertEquals("TRANSFER", event.getTransactionType());
    }

    @Test
    @DisplayName("publish() - category null → categoryName is null in event")
    void publish_categoryNull_categoryNameIsNull() {
        Transaction tx = buildTx(null);

        ArgumentCaptor<TransactionKafkaEvent> captor =
                ArgumentCaptor.forClass(TransactionKafkaEvent.class);

        transactionEventPublisher.publish(tx);

        verify(kafkaTemplate).send(any(), any(), captor.capture());

        assertNull(captor.getValue().getCategoryName());
    }

    @Test
    @DisplayName("publish() - category present → categoryName set correctly")
    void publish_categoryPresent_categoryNameSet() {
        TransactionCategory category = new TransactionCategory();
        category.setName("ENTERTAINMENT");

        Transaction tx = buildTx(category);

        ArgumentCaptor<TransactionKafkaEvent> captor =
                ArgumentCaptor.forClass(TransactionKafkaEvent.class);

        transactionEventPublisher.publish(tx);

        verify(kafkaTemplate).send(any(), any(), captor.capture());

        assertEquals("ENTERTAINMENT", captor.getValue().getCategoryName());
    }
}