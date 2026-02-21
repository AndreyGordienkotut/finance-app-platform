package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import core.core.exception.*;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountClient accountClient;
    @Mock
    private LimitService limitService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private CategoryService categoryService;
    @InjectMocks
    private TransactionService transactionService;

    private TransactionRequestDto transferDto;
    private Long userId;
    private String idempotencyKey;
    private final Long TX_ID = 10L;

    private AccountResponseDto fromAccount;
    private AccountResponseDto toAccount;
    private Transaction txCreated;
    private Transaction txInProgress;

    @BeforeEach
    void setUp() {
        transferDto = TransactionRequestDto.builder()
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .categoryId(10L)
                .build();
        userId = 1L;
        idempotencyKey = "test-key-123";

        fromAccount = AccountResponseDto.builder()
                .id(1L)
                .userId(userId)
                .currency(Currency.USD)
                .balance(BigDecimal.valueOf(200))
                .status(StatusAccount.ACTIVE)
                .build();
        toAccount = AccountResponseDto.builder()
                .id(2L)
                .userId(99L)
                .currency(Currency.USD)
                .balance(BigDecimal.valueOf(50))
                .status(StatusAccount.ACTIVE)
                .build();

        txCreated = Transaction.builder()
                .id(TX_ID)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.CREATED)
                .transactionType(TransactionType.TRANSFER)
                .step(TransactionStep.NONE)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();
        txInProgress = Transaction.builder()
                .id(TX_ID)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.PROCESSING)
                .transactionType(TransactionType.TRANSFER)
                .step(TransactionStep.NONE)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();
        lenient().when(categoryService.validateAndGetCategory(any(), any(), any())).thenReturn(null);
    }
    @Test
    @DisplayName("Succeed transfer (same currency)")
    void transfer_succeed_sameCurrency() {
        BigDecimal amount = new BigDecimal("100");
        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });
        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);
        assertNotNull(result);

        verify(accountClient).debit(
                eq(1L),
                eq(amount),
                eq(TX_ID)
        );
        verify(accountClient).credit(
                eq(2L),
                eq(amount),
                eq(TX_ID)
        );
    }
    @Test
    @DisplayName("Transfer with Currency Conversion")
    void transfer_succeed_withCurrencyConversion() {
        BigDecimal amount = new BigDecimal("100");
        BigDecimal rate = new BigDecimal("0.9");
        BigDecimal converted = new BigDecimal("90.0");

        fromAccount.setCurrency(Currency.USD);
        toAccount.setCurrency(Currency.EUR);

        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(exchangeRateService.getRate(Currency.USD, Currency.EUR))
                .thenReturn(rate);

        when(exchangeRateService.convert(amount, rate))
                .thenReturn(converted);

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);

        verify(accountClient).debit(
                eq(1L),
                eq(amount),
                eq(TX_ID)
        );
        verify(accountClient).credit(
                eq(2L),
                eq(converted),
                eq(TX_ID)
        );
        verify(exchangeRateService)
                .getRate(Currency.USD, Currency.EUR);

        verify(exchangeRateService)
                .convert(amount, rate);
    }
    @Test
    @DisplayName("Should throw LimitExceededException and stop process")
    void testLimitExceeded() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        doThrow(new LimitExceededException("Limit exceeded"))
                .when(limitService).checkTransactionLimit(any(), any());

        assertThrows(LimitExceededException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(exchangeRateService);
    }
    @Test
    @DisplayName("SAGA: Debit failed -> FAILED status")
    void testDebitFails() {
        when(accountClient.getAccountById(anyLong())).thenReturn(fromAccount).thenReturn(toAccount);
        when(transactionRepository.save(any())).thenReturn(txCreated);
        when(transactionRepository.findById(any())).thenReturn(Optional.of(txCreated));

        doNothing().when(accountClient).debit(any(), any(), any());
        doThrow(mock(FeignException.class)).when(accountClient).credit(any(), any(), any());

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(accountClient).credit(eq(1L), any(), any());

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(Status.FAILED, txCaptor.getValue().getStatus());
    }
    @Test
    @DisplayName("Deposit: Success")
    void testDepositSuccess() {
        DepositRequestDto depositDto = new DepositRequestDto(1L, new BigDecimal("500"),1L);
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(transactionRepository.save(any())).thenReturn(txCreated);
        when(transactionRepository.findById(any())).thenReturn(Optional.of(txCreated));

        transactionService.deposit(depositDto, idempotencyKey, userId);

        verify(accountClient).credit(eq(1L), eq(new BigDecimal("500")), anyLong());
        verify(accountClient, never()).debit(any(), any(), any());
    }
    @Test
    @DisplayName("Credit failed - rollback succeed")
    void transfer_creditFailed_rollbackSucceed() {
        BigDecimal amount = new BigDecimal("100");

        fromAccount.setCurrency(Currency.USD);
        toAccount.setCurrency(Currency.USD);
        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });
        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient)
                .debit(1L, amount, TX_ID);
        doThrow(new RuntimeException("Credit failed"))
                .when(accountClient)
                .credit(2L, amount, TX_ID);
        doNothing().when(accountClient)
                .credit(1L, amount, TX_ID);

        assertThrows(RuntimeException.class, () ->
                transactionService.transfer(transferDto, userId, idempotencyKey)
        );

        verify(accountClient).debit(1L, amount, TX_ID);
        verify(accountClient).credit(2L, amount, TX_ID);
        verify(accountClient).credit(1L, amount, TX_ID);

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }
    @Test
    @DisplayName("Credit failed - rollback failed")
    void transfer_creditFailed_rollbackFailed() {
        BigDecimal amount = new BigDecimal("100");

        fromAccount.setCurrency(Currency.USD);
        toAccount.setCurrency(Currency.USD);

        AtomicReference<Transaction> savedTx = new AtomicReference<>();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });
        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        doNothing().when(accountClient)
                .debit(1L, amount, TX_ID);
        doThrow(new RuntimeException("Credit failed"))
                .when(accountClient)
                .credit(2L, amount, TX_ID);
        doThrow(new RuntimeException("Rollback failed"))
                .when(accountClient)
                .credit(1L, amount, TX_ID);
        assertThrows(RuntimeException.class, () ->
                transactionService.transfer(transferDto, userId, idempotencyKey)
        );

        verify(accountClient).debit(1L, amount, TX_ID);
        verify(accountClient).credit(2L, amount, TX_ID);
        verify(accountClient).credit(1L, amount, TX_ID);

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }
    @Test
    @DisplayName("Idempotency: Existing COMPLETED transaction")
    void testIdempotency_Completed() {
        txInProgress.setStatus(Status.COMPLETED);
        txInProgress.setStep(TransactionStep.CREDIT_DONE);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(txInProgress));

        TransactionResponseDto result = transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);
        assertEquals(Status.COMPLETED, result.getStatus());

        verify(accountClient, times(1)).getAccountById(1L);
        verify(accountClient, times(1)).getAccountById(2L);
        verify(transactionRepository, never()).save(any());
    }
    @Test
    @DisplayName("Idempotency: Existing PROCESSING transaction -> Conflict")
    void testIdempotency_Processing() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(txInProgress));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        assertNotNull(ex.getPayload());

        verify(accountClient, times(1)).getAccountById(1L);
        verify(accountClient, times(1)).getAccountById(2L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: Concurrent creation -> DataIntegrityViolation")
    void testIdempotency_ConcurrentCreation() {

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(txCreated));

        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        assertThrows(ConflictException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
    }
    @Test
    @DisplayName("Validate: Foreign source account -> NotFound (via validateAccountOwnership)")
    void testValidateForeignSourceAccount() {
        when(accountClient.getAccountById(1L)).thenThrow(new NotFoundException("Account not found or access denied."));

        assertThrows(NotFoundException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(accountClient, times(1)).getAccountById(1L);
        verify(accountClient, never()).getAccountById(2L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validate: Same account -> BadRequest")
    void testValidateSameAccount() {
        transferDto.setTargetAccountId(1L);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
    }

    @Test
    @DisplayName("Validate: Account closed -> BadRequest")
    void testValidateAccountClosed() {
        fromAccount.setStatus(StatusAccount.CLOSED);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
    }

    @Test
    @DisplayName("Validate: Not enough money -> BadRequest")
    void testValidateNotEnoughMoney() {
        fromAccount.setBalance(BigDecimal.valueOf(50));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
    }
    @Test
    @DisplayName("Unexpected exception during SAGA -> BadRequestException")
    void testUnexpectedExceptionDuringSaga() {
        txCreated.setTargetAmount(BigDecimal.valueOf(100));
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        doThrow(new RuntimeException("Database connection lost"))
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(3)).save(txCaptor.capture());

        verify(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        verify(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        verify(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());


        Transaction last = txCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .findFirst().orElseThrow();

        assertEquals(Status.FAILED, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Transfer failed"));
    }
    @Test
    @DisplayName("Retry: Success on second attempt")
    void testRetry_SucceedsOnSecondAttempt() {
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txInProgress);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txInProgress));
        var lockEx = new org.springframework.dao.PessimisticLockingFailureException("Locked");
        var conflictEx = new ConflictException("Busy", lockEx);

        doThrow(conflictEx).doNothing().when(accountClient).debit(any(), any(), any());

        TransactionResponseDto result = transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.COMPLETED, result.getStatus());
        verify(accountClient, times(2)).debit(any(), any(), any());
    }

    @Test
    @DisplayName("Retry: Exhausted attempts should throw Conflict")
    void testRetry_ExhaustedAttempts() {
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txInProgress);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txInProgress));

        var lockEx = new org.springframework.dao.PessimisticLockingFailureException("Locked");
        var conflictEx = new ConflictException("Busy", lockEx);

        doThrow(conflictEx).when(accountClient).debit(any(), any(), any());

        assertThrows(ConflictException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(accountClient, times(3)).debit(any(), any(), any());
    }

    @Test
    @DisplayName("Retry: No retry on BadRequest")
    void testRetry_NoRetryOnBadRequest() {
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txInProgress);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txInProgress));

        doThrow(new BadRequestException("Insufficient funds")).when(accountClient).debit(any(), any(), any());

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(accountClient, times(1)).debit(any(), any(), any());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }
}
