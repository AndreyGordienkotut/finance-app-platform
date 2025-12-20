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
import transaction_service.transaction_service.dto.TransactionRequestDto;
import core.core.exception.*;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    @InjectMocks
    private TransactionService transactionService;

    private TransactionRequestDto transactionRequestDto;
    private Long userId;
    private String idempotencyKey;
    private AccountResponseDto fromAccount;
    private AccountResponseDto toAccount;
    private Transaction txInProgress;
    private Transaction txCreated;

    @BeforeEach
    void setUp() {
        transactionRequestDto = TransactionRequestDto.builder()
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
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
                .id(10L)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.CREATED)
                .typeTransaction(TypeTransaction.TRANSFER)
                .step(TransactionStep.NONE)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();

        txInProgress = Transaction.builder()
                .id(10L)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.PROCESSING)
                .typeTransaction(TypeTransaction.TRANSFER)
                .step(TransactionStep.NONE)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    @Test
    @DisplayName("Succeed transfer")
    void testSuccessfulTransfer() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        doNothing().when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(txCreated);

        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        transactionService.transfer(transactionRequestDto, userId, idempotencyKey);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(3)).save(txCaptor.capture());

        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);

        assertEquals(Status.COMPLETED, last.getStatus());
        assertEquals(TransactionStep.CREDIT_DONE, last.getStep());
        assertNotNull(last.getUpdatedAt());

        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
    }
    @Test
    @DisplayName("SAGA: Debit failed -> FAILED status")
    void testDebitFails() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("Debit failed");

        doThrow(feignException).when(accountClient).debit(eq(1L), any(), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(2)).save(txCaptor.capture());

        Transaction last = txCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .findFirst().orElseThrow();

        assertEquals(Status.FAILED, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Debit failed"));

        verify(accountClient, never()).credit(anyLong(), any(), anyLong());
    }
    @Test
    @DisplayName("Credit failed - rollback succeed")
    void testCreditFailsRollbackSuccess() {
        fromAccount.setBalance(BigDecimal.valueOf(1000));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("Credit failed");

        doThrow(feignException)
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        doNothing().when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(4)).save(txCaptor.capture());

        Transaction last = txCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .findFirst().orElseThrow();

        assertEquals(Status.FAILED, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Transfer failed"));

        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
    }
    @Test
    @DisplayName("Credit failed - rollback failed")
    void testCreditFailsRollbackFailed() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        FeignException feignCredit = mock(FeignException.class);
        when(feignCredit.getMessage()).thenReturn("Credit failed");

        FeignException feignCompensate = mock(FeignException.class);

        doThrow(feignCredit)
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());
        doThrow(new RuntimeException("Compensation failed", feignCompensate))
                .when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(txCreated);

        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(txCreated));

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(4)).save(txCaptor.capture());

        Transaction last = txCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .findFirst().orElseThrow();

        assertEquals(Status.FAILED, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Compensation failed"));

        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(1L, BigDecimal.valueOf(100), 10L);
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

        TransactionResponseDto result = transactionService.transfer(transactionRequestDto, userId, idempotencyKey);

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
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

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
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

        assertNotNull(ex.getPayload());
        verify(transactionRepository, times(2)).findByIdempotencyKey(idempotencyKey);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }
    @Test
    @DisplayName("Validate: Foreign source account -> NotFound (via validateAccountOwnership)")
    void testValidateForeignSourceAccount() {
        when(accountClient.getAccountById(1L)).thenThrow(new NotFoundException("Account not found or access denied."));

        assertThrows(NotFoundException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

        verify(accountClient, times(1)).getAccountById(1L);
        verify(accountClient, never()).getAccountById(2L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validate: Same account -> BadRequest")
    void testValidateSameAccount() {
        transactionRequestDto.setTargetAccountId(1L);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));
    }

    @Test
    @DisplayName("Validate: Account closed -> BadRequest")
    void testValidateAccountClosed() {
        fromAccount.setStatus(StatusAccount.CLOSED);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));
    }

    @Test
    @DisplayName("Validate: Currency mismatch -> BadRequest")
    void testValidateDifferentCurrencies() {
        toAccount.setCurrency(Currency.EUR);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));
    }

    @Test
    @DisplayName("Validate: Not enough money -> BadRequest")
    void testValidateNotEnoughMoney() {
        fromAccount.setBalance(BigDecimal.valueOf(50));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));
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
                () -> transactionService.transfer(transactionRequestDto, userId, idempotencyKey));

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
