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
//    @Test
//    @DisplayName("Succeed transfer (Same Currency)")
//    void testSuccessfulTransfer() {
//        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
//        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
//        when(categoryService.validateAndGetCategory(any(), any(), any())).thenReturn(null);
//        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
//        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
//        when(transactionRepository.findById(100L)).thenReturn(Optional.of(txCreated));
//
//        transactionService.transfer(transferDto, userId, idempotencyKey);
//
//        verify(limitService).checkTransactionLimit(eq(userId), any());
//        verify(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), eq(100L));
//        verify(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), eq(100L));
//
//        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
//        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
//        assertEquals(Status.COMPLETED, txCaptor.getAllValues().get(txCaptor.getAllValues().size()-1).getStatus());
//    }
//    @Test
//    @DisplayName("Transfer with Currency Conversion")
//    void testTransferWithConversion() {
//        toAccount.setCurrency(Currency.EUR);
//        BigDecimal rate = new BigDecimal("0.9");
//        BigDecimal expectedTargetAmount = new BigDecimal("90.00");
//
//        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
//        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
//
//        when(exchangeRateService.getRate(Currency.USD, Currency.EUR)).thenReturn(rate);
//        when(exchangeRateService.convert(any(), any())).thenReturn(expectedTargetAmount);
//
//        when(transactionRepository.save(any(Transaction.class)))
//                .thenAnswer(invocation -> {
//                    Transaction saved = invocation.getArgument(0);
//                    saved.setId(TX_ID);
//                    return saved;
//                });
//
//        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(txCreated));
//
//        transactionService.transfer(transferDto, userId, idempotencyKey);
//
//        verify(exchangeRateService).getRate(Currency.USD, Currency.EUR);
//        verify(accountClient).credit(eq(2L), eq(expectedTargetAmount), eq(TX_ID));
//    }
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
//    @Test
//    @DisplayName("Credit failed - rollback succeed")
//    void testCreditFailsRollbackSuccess() {
//        fromAccount.setBalance(BigDecimal.valueOf(1000));
//
//        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
//        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
//
//        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
//
//        FeignException feignException = mock(FeignException.class);
//        when(feignException.getMessage()).thenReturn("Credit failed");
//
//        doThrow(feignException)
//                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());
//
//        doNothing().when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
//
//        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
//        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
//        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));
//
//        assertThrows(BadRequestException.class,
//                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
//
//        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
//        verify(transactionRepository, atLeast(4)).save(txCaptor.capture());
//
//        Transaction last = txCaptor.getAllValues().stream()
//                .filter(t -> t.getStatus() == Status.FAILED)
//                .findFirst().orElseThrow();
//
//        assertEquals(Status.FAILED, last.getStatus());
//        assertTrue(last.getErrorMessage().contains("Transfer failed"));
//
//        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
//        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
//        verify(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
//    }
//    @Test
//    @DisplayName("Credit failed - rollback failed")
//    void testCreditFailsRollbackFailed() {
//        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
//        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
//
//        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
//
//        FeignException feignCredit = mock(FeignException.class);
//        when(feignCredit.getMessage()).thenReturn("Credit failed");
//
//        FeignException feignCompensate = mock(FeignException.class);
//
//        doThrow(feignCredit)
//                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());
//        doThrow(new RuntimeException("Compensation failed", feignCompensate))
//                .when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
//
//        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
//
//        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
//
//        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));
//
//        assertThrows(BadRequestException.class,
//                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
//
//        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
//        verify(transactionRepository, atLeast(4)).save(txCaptor.capture());
//
//        Transaction last = txCaptor.getAllValues().stream()
//                .filter(t -> t.getStatus() == Status.FAILED)
//                .findFirst().orElseThrow();
//
//        assertEquals(Status.FAILED, last.getStatus());
//        assertTrue(last.getErrorMessage().contains("Compensation failed"));
//
//        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
//        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
//        verify(accountClient).credit(1L, BigDecimal.valueOf(100), 10L);
//    }
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
                .thenThrow(DataIntegrityViolationException.class);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        assertNotNull(ex.getPayload());
        verify(transactionRepository, times(2)).findByIdempotencyKey(idempotencyKey);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
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

//    @Test
//    @DisplayName("Validate: Currency mismatch -> BadRequest")
//    void testValidateDifferentCurrencies() {
//        toAccount.setCurrency(Currency.EUR);
//
//        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
//        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
//
//        assertThrows(BadRequestException.class,
//                () -> transactionService.transfer(transferDto, userId, idempotencyKey));
//    }

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
    @DisplayName("Unexpected exception during SAGA -> InternalServerError")
    void testUnexpectedExceptionDuringSaga() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        doThrow(new RuntimeException("Database connection lost"))
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transferDto, userId, idempotencyKey));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(3)).save(txCaptor.capture());

        verify(accountClient, never()).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        Transaction last = txCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .findFirst().orElseThrow();

        assertEquals(Status.FAILED, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Unexpected error"));
    }
}
