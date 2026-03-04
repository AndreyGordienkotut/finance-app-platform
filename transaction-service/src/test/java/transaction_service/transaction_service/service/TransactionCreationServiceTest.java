package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionCreationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountOperationService accountOperationService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private LimitService limitService;

    @InjectMocks
    private TransactionCreationService creationService;

    private final Long userId = 100L;
    private final Long sourceId = 1L;
    private final Long targetId = 2L;

    @Test
    @DisplayName("Create transfer transaction with same currency")
    void testCreateTransactionSameCurrency() {
        AccountResponseDto targetAcc = AccountResponseDto.builder()
                .id(targetId)
                .currency(Currency.USD)
                .balance(BigDecimal.valueOf(1000))
                .status(StatusAccount.ACTIVE)
                .build();

        when(accountOperationService.getAccountById(targetId)).thenReturn(targetAcc);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction tx = creationService.createTransaction(
                sourceId,
                targetId,
                BigDecimal.valueOf(100),
                Currency.USD,
                TransactionType.TRANSFER,
                "idempotency-1",
                userId,
                new TransactionCategory()
        );

        assertEquals(Status.CREATED, tx.getStatus());
        assertEquals(BigDecimal.valueOf(100), tx.getAmount());
        assertEquals(BigDecimal.ONE, tx.getExchangeRate());
        verify(limitService).checkTransactionLimit(userId, BigDecimal.valueOf(100));
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("Create transfer transaction with different currency")
    void testCreateTransactionDifferentCurrency() {
        AccountResponseDto targetAcc = AccountResponseDto.builder()
                .id(targetId)
                .currency(Currency.EUR)
                .balance(BigDecimal.valueOf(1000))
                .status(StatusAccount.ACTIVE)
                .build();

        when(accountOperationService.getAccountById(targetId)).thenReturn(targetAcc);
        when(exchangeRateService.getRate(Currency.USD, Currency.EUR)).thenReturn(BigDecimal.valueOf(0.9));
        when(exchangeRateService.convert(BigDecimal.valueOf(100), BigDecimal.valueOf(0.9)))
                .thenReturn(BigDecimal.valueOf(90));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction tx = creationService.createTransaction(
                sourceId,
                targetId,
                BigDecimal.valueOf(100),
                Currency.USD,
                TransactionType.TRANSFER,
                "idempotency-2",
                userId,
                new TransactionCategory()
        );

        assertEquals(Status.CREATED, tx.getStatus());
        assertEquals(BigDecimal.valueOf(100), tx.getAmount());
        assertEquals(BigDecimal.valueOf(90), tx.getTargetAmount());
        assertEquals(BigDecimal.valueOf(0.9), tx.getExchangeRate());
        verify(limitService).checkTransactionLimit(userId, BigDecimal.valueOf(90));
        verify(transactionRepository).save(tx);
    }
}