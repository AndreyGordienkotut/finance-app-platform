package transaction_service.transaction_service.service;

import core.core.AccountResponseDto;
import core.core.Currency;
import core.core.StatusAccount;
import feign.FeignException;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.exception.BadRequestException;
import transaction_service.transaction_service.exception.InternalServerErrorException;
import transaction_service.transaction_service.model.RollBackStatus;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
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
    @InjectMocks
    private TransactionService transactionService;
    @Mock
    private AccountClient accountClient;


    private TransactionRequestDto transactionRequestDto;
    private Long userId;
    private AccountResponseDto fromAccount;
    private AccountResponseDto toAccount;
    private Transaction pendingTx;
    private Transaction successTx;

    @BeforeEach
    void setUp() {
         transactionRequestDto = TransactionRequestDto.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .build();
        userId = 1L;
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
         pendingTx = Transaction.builder()
                .id(10L)
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.PENDING)
                .rollbackStatus(RollBackStatus.NONE)
                .createdAt(LocalDateTime.now())
                .build();



    }

    @Test
    @DisplayName("Succeed transfer")
    void testSuccessfulTransfer(){
        successTx = Transaction.builder()
                .id(10L)
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .status(Status.SUCCESS)
                .rollbackStatus(RollBackStatus.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        doNothing().when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });

        when(transactionRepository.findById(10L))
                .thenReturn(Optional.of(pendingTx));

        transactionService.transfer(transactionRequestDto, userId);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);
        assertEquals(Status.SUCCESS, last.getStatus());
        assertEquals(1L, last.getFromAccountId());
        assertEquals(2L, last.getToAccountId());
        assertEquals(BigDecimal.valueOf(100), last.getAmount());
        assertNotNull(last.getUpdatedAt());
        assertTrue(last.getErrorMessage() == null || last.getErrorMessage().isEmpty());

        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
    }
    @Test
    @DisplayName("Debit failed")
    void testDebitFails(){
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("Debit failed");

        doThrow(feignException).when(accountClient).debit(eq(1L), any(), anyLong());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });

        when(transactionRepository.findById(10L))
                .thenReturn(Optional.of(pendingTx));

        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, userId));
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);

        assertEquals("Debit failed: Debit failed", last.getErrorMessage());
        assertNotNull(last.getUpdatedAt());
        assertEquals(Status.FAILED, last.getStatus());
        verify(accountClient,never()).credit(anyLong(),any(),anyLong());
    }
    @Test
    @DisplayName("Credit failed - rollback succeed")
    void testCreditFailsRollbackSuccess(){
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("Credit failed");

        doThrow(feignException)
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());
        doNothing().when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(pendingTx);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

         transactionService.transfer(transactionRequestDto, userId);
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);
        assertEquals(Status.FAILED, last.getStatus());
        assertEquals(RollBackStatus.SUCCESS, last.getRollbackStatus());
        assertNotNull(last.getUpdatedAt());
        assertTrue(last.getErrorMessage() == null || last.getErrorMessage().isEmpty()
                || last.getErrorMessage().contains("Credit failed. Rollback applied"));


        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());

    }
    @Test
    @DisplayName("Credit failed - rollback failed")
    void testCreditFailsRollbackFailed(){

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("Credit failed");

        doThrow(feignException)
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());
        doThrow(feignException)
                .when(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pendingTx);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));
         transactionService.transfer(transactionRequestDto, userId);
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);

        assertEquals(Status.FAILED, last.getStatus());
        assertEquals(RollBackStatus.FAILED, last.getRollbackStatus());
        assertNotNull(last.getUpdatedAt());
        assertTrue(last.getErrorMessage().contains("Rollback failed"));

        verify(accountClient).debit(1L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(2L, BigDecimal.valueOf(100), 10L);
        verify(accountClient).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
    }
    @Test
    @DisplayName("Unexpected exception → ERROR")
    void testUnexpectedException() {
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);

        doNothing().when(accountClient).debit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
        doThrow(new RuntimeException("Unexpected crash"))
                .when(accountClient).credit(eq(2L), eq(BigDecimal.valueOf(100)), anyLong());

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) {
                        t.setId(10L);
                    }
                    return t;
                });

        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(InternalServerErrorException.class,
                () -> transactionService.transfer(transactionRequestDto, userId));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        List<Transaction> allSaves = txCaptor.getAllValues();
        Transaction last = allSaves.get(allSaves.size() - 1);

        assertEquals(Status.ERROR, last.getStatus());
        assertTrue(last.getErrorMessage().contains("Unexpected crash"));
        assertNotNull(last.getUpdatedAt());

        verify(accountClient, never()).credit(eq(1L), eq(BigDecimal.valueOf(100)), anyLong());
    }
    @Test
    @DisplayName("Validate: account does not belong to user → BadRequest")
    void testValidateForeignAccount() {
        fromAccount.setUserId(999L);
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, 100L));
    }
    @Test
    @DisplayName("Validate: same account → BadRequest")
    void testValidateSameAccount() {
        transactionRequestDto.setToAccountId(1L);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, 100L));
    }
    @Test
    @DisplayName("Validate: account closed → BadRequest")
    void testValidateAccountClosed() {
        transactionRequestDto.setToAccountId(1L);
        fromAccount.setStatus(StatusAccount.CLOSED);
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, 100L));

    }
    @Test
    @DisplayName("Validate: currency mismatch → BadRequest")
    void testValidateDifferentCurrencies() {
        toAccount.setCurrency(Currency.EUR);

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, 100L));
    }
    @Test
    @DisplayName("Validate: not enough money → BadRequest")
    void testValidateNotEnoughMoney() {
        fromAccount.setBalance(BigDecimal.valueOf(50));

        when(accountClient.getAccountById(1L)).thenReturn(fromAccount);
        when(accountClient.getAccountById(2L)).thenReturn(toAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    if (t.getId() == null) t.setId(10L);
                    return t;
                });
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.of(pendingTx));
        assertThrows(BadRequestException.class,
                () -> transactionService.transfer(transactionRequestDto, 100L));
    }
}
