package transaction_service.transaction_service.service;

import core.core.enums.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionCreationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionCreationService creationService;

    private final Long userId = 100L;
    private final Long sourceId = 1L;
    private final Long targetId = 2L;

    @Test
    @DisplayName("Create transaction - saves with correct fields")
    void createTransaction_savesWithCorrectFields() {
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction tx = creationService.createTransaction(
                sourceId, targetId,
                BigDecimal.valueOf(100),
                Currency.USD,
                TransactionType.TRANSFER,
                "idempotency-1",
                userId,
                new TransactionCategory(),
                BigDecimal.ONE,
                BigDecimal.valueOf(100)
        );

        assertEquals(Status.CREATED, tx.getStatus());
        assertEquals(TransactionStep.NONE, tx.getStep());
        assertEquals(BigDecimal.valueOf(100), tx.getAmount());
        assertEquals(BigDecimal.valueOf(100), tx.getTargetAmount());
        assertEquals(BigDecimal.ONE, tx.getExchangeRate());
        assertEquals(Currency.USD, tx.getCurrency());
        assertEquals(userId, tx.getUserId());
        verify(transactionRepository).save(any());
    }
    @Test
    @DisplayName("Create transaction with exchange rate - targetAmount calculated correctly")
    void createTransaction_withRate_targetAmountCorrect() {
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction tx = creationService.createTransaction(
                sourceId, targetId,
                BigDecimal.valueOf(100),
                Currency.USD,
                TransactionType.TRANSFER,
                "idempotency-2",
                userId,
                new TransactionCategory(),
                new BigDecimal("0.9"),
                new BigDecimal("90.00")
        );

        assertEquals(new BigDecimal("0.9"), tx.getExchangeRate());
        assertEquals(new BigDecimal("90.00"), tx.getTargetAmount());
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("Create deposit - sourceAccountId is null")
    void createTransaction_deposit_sourceIsNull() {
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction tx = creationService.createTransaction(
                null, targetId,
                BigDecimal.valueOf(500),
                Currency.USD,
                TransactionType.DEPOSIT,
                "idempotency-3",
                userId,
                null,
                BigDecimal.ONE,
                BigDecimal.valueOf(500)
        );

        assertNull(tx.getSourceAccountId());
        assertEquals(TransactionType.DEPOSIT, tx.getTransactionType());
        verify(transactionRepository).save(any());
    }
}