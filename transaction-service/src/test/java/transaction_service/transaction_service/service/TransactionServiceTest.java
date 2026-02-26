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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import core.core.exception.*;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.mapper.TransactionMapper;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;
import org.springframework.dao.PessimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private AccountClient accountClient;
    @Mock
    private LimitService limitService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private FinancialOperationStrategy transferStrategy;
    @Mock
    private FinancialOperationStrategy depositStrategy;
    @Mock
    private FinancialOperationStrategy withdrawStrategy;
    @Mock
    private  TransactionMapper transactionMapper;
    @Mock
    private  ApplicationEventPublisher eventPublisher;


    private TransactionService transactionService;

    private TransactionRequestDto transferDto;
    private Long userId;
    private String idempotencyKey;
    private final Long TX_ID = 10L;

    private AccountResponseDto fromAccount;
    private AccountResponseDto toAccount;
    private Transaction txCreated;
    private Transaction txInProgress;
    private AtomicReference<Transaction> storedTx;
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
                limitService,
                accountClient,
                exchangeRateService,
                categoryService,
                transactionMapper,
                eventPublisher,
                strategyList
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
        storedTx = new AtomicReference<>(txInProgress);
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
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenReturn(new TransactionResponseDto());
        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);
        assertNotNull(result);

        verify(transferStrategy).execute(
                any(Transaction.class),
                eq(1L),
                eq(2L),
                eq(BigDecimal.valueOf(100))
        );
    }
    @Test
    @DisplayName("Transfer with currency conversion → strategy invoked")
    void transfer_succeed_withCurrencyConversion() {

        BigDecimal rate = new BigDecimal("0.9");
        BigDecimal converted = new BigDecimal("90.0");

        fromAccount.setCurrency(Currency.USD);
        toAccount.setCurrency(Currency.EUR);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        when(exchangeRateService.getRate(Currency.USD, Currency.EUR))
                .thenReturn(rate);

        when(exchangeRateService.convert(BigDecimal.valueOf(100), rate))
                .thenReturn(converted);

        when(transactionMapper.toDto(any()))
                .thenReturn(new TransactionResponseDto());

        transactionService.transfer(transferDto, userId, idempotencyKey);

        verify(exchangeRateService).getRate(Currency.USD, Currency.EUR);
        verify(exchangeRateService).convert(BigDecimal.valueOf(100), rate);

        verify(transferStrategy).execute(
                any(Transaction.class),
                eq(1L),
                eq(2L),
                eq(converted)
        );
    }

    @Test
    @DisplayName("SAGA: Debit failed -> FAILED status strategy")
    void testDebitFails_Strategy() {

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        AtomicReference<Transaction> savedTx = new AtomicReference<>(txCreated);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
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

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }
    @Test
    @DisplayName("Deposit success")
    void testDepositSuccess_Strategy() {

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);

        AtomicReference<Transaction> savedTx = new AtomicReference<>(txCreated);

        DepositRequestDto depositDto =
                new DepositRequestDto(1L, new BigDecimal("500"), 1L);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        doNothing().when(depositStrategy)
                .execute(any(Transaction.class), eq(null), eq(1L), eq(new BigDecimal("500")));

        when(transactionMapper.toDto(any())).thenReturn(new TransactionResponseDto());

        TransactionResponseDto result =
                transactionService.deposit(depositDto, idempotencyKey, userId);

        assertNotNull(result);

        verify(depositStrategy)
                .execute(any(Transaction.class), eq(null), eq(1L), eq(new BigDecimal("500")));

        assertEquals(Status.COMPLETED, savedTx.get().getStatus());
    }
    @Test
    @DisplayName("Strategy throws -> status FAILED")
    void transfer_creditFailed_rollbackSucceed() {

        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        doThrow(new RuntimeException("Credit failed"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy).execute(any(), any(), any(), any());

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }
    @Test
    @DisplayName("Transfer credit failed -> FAILED status")
    void transfer_creditFailed_rollbackHandled() {

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        AtomicReference<Transaction> savedTx = new AtomicReference<>(txCreated);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        doThrow(new RuntimeException("Credit failed"))
                .when(transferStrategy)
                .execute(any(Transaction.class), eq(1L), eq(2L), eq(new BigDecimal("100")));

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy)
                .execute(any(Transaction.class), eq(1L), eq(2L), eq(new BigDecimal("100")));

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }

    @Test
    @DisplayName("Unexpected exception during SAGA -> FAILED + InternalServerErrorException")
    void testUnexpectedExceptionDuringSaga() {

        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    tx.setId(TX_ID);
                    savedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(savedTx.get()));

        doThrow(new RuntimeException("DB lost"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        assertEquals(Status.FAILED, savedTx.get().getStatus());
    }

    @Test
    @DisplayName("Should throw LimitExceededException and stop process")
    void testLimitExceeded() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        doThrow(new LimitExceededException("Limit exceeded"))
                .when(limitService).checkTransactionLimit(any(), any());

        assertThrows(LimitExceededException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(exchangeRateService);
    }
    @Test
    @DisplayName("Idempotency: Existing COMPLETED transaction")
    void testIdempotency_Completed() {
        txInProgress.setStatus(Status.COMPLETED);
        txInProgress.setStep(TransactionStep.CREDIT_DONE);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    return TransactionResponseDto.builder()
                            .id(tx.getId())
                            .status(tx.getStatus())
                            .amount(tx.getAmount())
                            .build();
                });
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
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    return TransactionResponseDto.builder()
                            .id(tx.getId())
                            .status(tx.getStatus())
                            .amount(tx.getAmount())
                            .build();
                });

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.PROCESSING, result.getStatus());


        verify(accountClient, times(1)).getAccountById(1L);
        verify(accountClient, times(1)).getAccountById(2L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: Concurrent creation -> DataIntegrityViolation")
    void testIdempotency_ConcurrentCreation() {

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    return TransactionResponseDto.builder()
                            .id(tx.getId())
                            .status(tx.getStatus())
                            .amount(tx.getAmount())
                            .build();
                });
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(txCreated));

        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        TransactionResponseDto result =
                transactionService.transfer(transferDto, userId, idempotencyKey);

        assertNotNull(result);
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
    @DisplayName("Retry: Success on second attempt")
    void testRetry_SucceedsOnSecondAttempt() {
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    if (tx.getId() == null) {
                        tx.setId(TX_ID);
                    }
                    storedTx.set(tx);
                    return tx;
                });
        when(transactionMapper.toDto(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    return TransactionResponseDto.builder()
                            .id(tx.getId())
                            .amount(tx.getAmount())
                            .status(tx.getStatus())
                            .build();
                });
        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedTx.get()));
        var lockEx = new org.springframework.dao.PessimisticLockingFailureException("Locked");
        var conflictEx = new ConflictException("Busy", lockEx);

        doThrow(conflictEx)
                .doNothing()
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        TransactionResponseDto result = transactionService.transfer(transferDto, userId, idempotencyKey);

        assertEquals(Status.COMPLETED, result.getStatus());
        verify(transferStrategy, times(2))
                .execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Retry: Exhausted attempts should throw Conflict")
    void testRetry_ExhaustedAttempts() {
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txInProgress);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txInProgress));


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
        when(accountClient.getAccountById(any())).thenReturn(fromAccount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txInProgress);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txInProgress));

        doThrow(new BadRequestException("Insufficient funds"))
                .when(transferStrategy)
                .execute(any(), any(), any(), any());

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        verify(transferStrategy, times(1))
                .execute(any(), any(), any(), any());
        verify(transactionRepository, times(3)).save(any(Transaction.class));
    }
    @Test
    @DisplayName("Transfer: Success should call strategy")
    void transfer_success_callsStrategy() {

        AtomicReference<Transaction> storedTx = new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    if (tx.getId() == null) {
                        tx.setId(TX_ID);
                    }
                    storedTx.set(tx);
                    return tx;
                });

        when(transactionRepository.findById(TX_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedTx.get()));

        transactionService.transfer(transferDto, userId, idempotencyKey);

        verify(transferStrategy).execute(any(), any(), any(), any());
    }
}
