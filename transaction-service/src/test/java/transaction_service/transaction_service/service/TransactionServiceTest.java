package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import core.core.exception.*;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.dto.ValidationResult;
import transaction_service.transaction_service.mapper.TransactionMapper;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;
import org.springframework.dao.PessimisticLockingFailureException;
import transaction_service.transaction_service.service.validate.AccountAccessService;
import transaction_service.transaction_service.service.validate.FraudValidationService;
import transaction_service.transaction_service.service.validate.ParallelValidationService;
import transaction_service.transaction_service.service.validate.TransactionValidationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    private CategoryService categoryService;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TransactionStateService transactionStateService;
    @Mock
    TransactionValidationService transactionValidationService;
    @Mock
    private AccountAccessService accountAccessService;
    @Mock
    private AccountOperationService accountOperationService;
    @Mock
    private TransactionCreationService transactionCreationService;
    @Mock
    private RetryBackoffService retryBackoffService;
    @Mock
    private FinancialOperationStrategy transferStrategy;
    @Mock
    private FinancialOperationStrategy depositStrategy;
    @Mock
    private FinancialOperationStrategy withdrawStrategy;
    @Mock
    private ParallelValidationService parallelValidationService;


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
        when(transferStrategy.getType()).thenReturn(TransactionType.TRANSFER);
        when(depositStrategy.getType()).thenReturn(TransactionType.DEPOSIT);
        when(withdrawStrategy.getType()).thenReturn(TransactionType.WITHDRAW);
        List<FinancialOperationStrategy> strategyList = List.of(
                transferStrategy, depositStrategy, withdrawStrategy
        );

        transactionService = new TransactionService(
                transactionRepository,
                categoryService,
                transactionMapper,
                transactionStateService,
                strategyList,
                transactionValidationService,
                accountAccessService,
                accountOperationService,
                transactionCreationService,
                retryBackoffService,
                parallelValidationService
        );

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
                .targetAmount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.CREATED)
                .transactionType(TransactionType.TRANSFER)
                .step(TransactionStep.NONE)
                .createdAt(Instant.now())
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
                .createdAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .build();
        lenient().when(categoryService.validateAndGetCategory(any(), any(), any())).thenReturn(null);
//        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
//                .thenReturn(ValidationResult.builder()
//                        .rate(BigDecimal.ONE)
//                        .targetAmount(BigDecimal.valueOf(100))
//                        .build());
    }

    @Test
    @DisplayName("Succeed transfer (same currency)")
    void transfer_succeed_sameCurrency() {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        when(transactionMapper.toDto(any()))
                .thenReturn(new TransactionResponseDto());

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);

        verify(transferStrategy).execute(
                eq(txCreated),
                eq(1L),
                eq(2L),
                eq(BigDecimal.valueOf(100))
        );

        verify(transactionStateService)
                .updateStatus(TX_ID, Status.COMPLETED, null);
    }

    @Test
    @DisplayName("SAGA: Debit failed -> FAILED status strategy")
    void testDebitFails_Strategy() {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        AtomicReference<Transaction> savedTx = new AtomicReference<>(txCreated);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenAnswer(invocation -> {
                    Transaction tx = txCreated;
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        doThrow(new BadRequestException("Debit failed"))
                .when(transferStrategy)
                .execute(any(Transaction.class), eq(1L), eq(2L), any());

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy)
                .execute(any(Transaction.class), eq(1L), eq(2L), any());
        verify(transactionStateService)
                .updateStatus(TX_ID, Status.FAILED, "Debit failed");
    }

    @Test
    @DisplayName("Deposit success")
    void deposit_success() {
        DepositRequestDto depositDto =
                new DepositRequestDto(1L, new BigDecimal("500"), 1L);

        Transaction depositTx = Transaction.builder()
                .id(TX_ID)
                .sourceAccountId(null)
                .targetAccountId(1L)
                .amount(new BigDecimal("500"))
                .targetAmount(new BigDecimal("500"))
                .currency(Currency.USD)
                .status(Status.CREATED)
                .transactionType(TransactionType.DEPOSIT)
                .step(TransactionStep.NONE)
                .idempotencyKey(idempotencyKey)
                .build();
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(depositTx);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(depositTx));

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(transactionMapper.toDto(any()))
                .thenReturn(new TransactionResponseDto());

        TransactionResponseDto result =
                transactionService.deposit(depositDto, idempotencyKey, userId);

        assertNotNull(result);

        verify(depositStrategy).execute(
                eq(depositTx),
                eq(null),
                eq(1L),
                eq(new BigDecimal("500"))
        );

        verify(transactionStateService)
                .updateStatus(TX_ID, Status.COMPLETED, null);
    }

    @Test
    @DisplayName("Strategy throws -> status FAILED")
    void transfer_strategyFails_statusFailed() {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        doThrow(new RuntimeException("Credit failed"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionStateService).updateStatus(
                eq(TX_ID),
                eq(Status.FAILED),
                eq("Unexpected error: Credit failed")
        );
    }


    @Test
    @DisplayName("Unexpected exception during SAGA -> FAILED + InternalServerErrorException")
    void testUnexpectedExceptionDuringSaga() {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        doThrow(new RuntimeException("DB lost"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionStateService, atLeastOnce())
                .updateStatus(eq(TX_ID), eq(Status.FAILED), contains("DB lost"));
    }

    @Test
    @DisplayName("Should throw LimitExceededException and stop process")
    void testLimitExceeded() {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenThrow(new LimitExceededException("Limit exceeded"));

        assertThrows(LimitExceededException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionRepository, never()).save(any());
        verify(transferStrategy, never()).execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Retries with backoff on pessimistic lock conflict")
    void retriesWithBackoffOnPessimisticLock() throws Exception {
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        when(transactionMapper.toDto(any()))
                .thenReturn(new TransactionResponseDto());

        ConflictException lockConflict = new ConflictException(
                "Account is busy",
                new PessimisticLockingFailureException("lock")
        );

        doThrow(lockConflict)
                .doThrow(lockConflict)
                .doNothing()
                .when(transferStrategy)
                .execute(any(Transaction.class), eq(1L), eq(2L), any());

        doNothing().when(retryBackoffService).backoff(anyInt());

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);
        verify(retryBackoffService).backoff(1);
        verify(retryBackoffService).backoff(2);
        verify(transactionStateService, atLeastOnce())
                .updateStatus(eq(TX_ID), eq(Status.PROCESSING), anyString());
    }
    @Test
    @DisplayName("Idempotency: Existing COMPLETED transaction")
    void testIdempotency_Completed() {

        txInProgress.setStatus(Status.COMPLETED);
        txInProgress.setStep(TransactionStep.CREDIT_DONE);

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(txInProgress));

        when(transactionMapper.toDto(txInProgress))
                .thenReturn(TransactionResponseDto.builder()
                        .id(txInProgress.getId())
                        .status(Status.COMPLETED)
                        .amount(txInProgress.getAmount())
                        .build());

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.COMPLETED, result.getStatus());

        verify(transferStrategy, never()).execute(any(), any(), any(), any());
        verify(transactionCreationService, never())
                .createTransaction(any(), any(), any(), any(), any(), any(), any(), any(),any(),any());
    }
    @Test
    @DisplayName("Idempotency: Existing PROCESSING transaction")
    void testIdempotency_Processing() {

        txInProgress.setStatus(Status.PROCESSING);

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(txInProgress));

        when(transactionMapper.toDto(txInProgress))
                .thenReturn(TransactionResponseDto.builder()
                        .id(txInProgress.getId())
                        .status(Status.PROCESSING)
                        .amount(txInProgress.getAmount())
                        .build());

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.PROCESSING, result.getStatus());

        verify(transferStrategy, never()).execute(any(), any(), any(), any());
        verify(transactionCreationService, never())
                .createTransaction(any(), any(), any(), any(), any(), any(), any(), any(),any(),any());
    }

    @Test
    @DisplayName("Idempotency: Concurrent creation -> DataIntegrityViolation")
    void testIdempotency_ConcurrentCreation() {

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    return TransactionResponseDto.builder()
                            .id(tx.getId())
                            .status(tx.getStatus())
                            .amount(tx.getAmount())
                            .build();
                });
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(txCreated));

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Retry: Success on second attempt")
    void testRetry_SucceedsOnSecondAttempt() {

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        var lockEx = new PessimisticLockingFailureException("Locked");
        var conflictEx = new ConflictException("Busy", lockEx);

        doThrow(conflictEx)
                .doNothing()
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        when(transactionMapper.toDto(any()))
                .thenReturn(TransactionResponseDto.builder()
                        .id(TX_ID)
                        .status(Status.COMPLETED)
                        .amount(BigDecimal.valueOf(100))
                        .build());

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.COMPLETED, result.getStatus());

        verify(transferStrategy, times(2))
                .execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Retry: Exhausted attempts should throw Conflict")
    void testRetry_ExhaustedAttempts() {

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        var lockEx = new PessimisticLockingFailureException("Locked");
        var conflictEx = new ConflictException("Busy", lockEx);

        doThrow(conflictEx)
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(ConflictException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy, times(3))
                .execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Retry: No retry on BadRequest")
    void testRetry_NoRetryOnBadRequest() {

        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);

        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(parallelValidationService.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ValidationResult.builder()
                        .rate(BigDecimal.ONE)
                        .targetAmount(BigDecimal.valueOf(100))
                        .build());
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionCreationService.createTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(),any(),any()))
                .thenReturn(txCreated);

        when(transactionRepository.findById(TX_ID))
                .thenReturn(Optional.of(txCreated));

        doThrow(new BadRequestException("Insufficient funds"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy, times(1))
                .execute(any(), any(), any(), any());
    }
    @Test
    @DisplayName("Fraud check triggered during transfer - blocks transaction creation")
    void transfer_fraudCheckCalled() {
        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);
        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        doThrow(new FraudDetectedException("Transaction amount is suspiciously large"))
                .when(parallelValidationService)
                .validate(any(), any(), any(), any(), any(), any());

        assertThrows(FraudDetectedException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionCreationService, never())
                .createTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Limit exceeded during transfer - blocks transaction creation")
    void transfer_limitExceeded_blocksCreation() {
        when(accountAccessService.validateAccountOwnership(1L, userId))
                .thenReturn(fromAccount);
        when(accountOperationService.getAccountById(2L))
                .thenReturn(toAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        doThrow(new LimitExceededException("Daily limit exceeded"))
                .when(parallelValidationService)
                .validate(any(), any(), any(), any(), any(), any());

        assertThrows(LimitExceededException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionCreationService, never())
                .createTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
