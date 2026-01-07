package transaction_service.transaction_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class TransactionRecoveryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionRecoveryService recoveryService;

    private Transaction stuckTx;

    @BeforeEach
    void setUp() {
        stuckTx = Transaction.builder()
                .id(999L)
                .status(Status.PROCESSING)
                .typeTransaction(TypeTransaction.TRANSFER)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(new BigDecimal("100.00"))
                .step(TransactionStep.DEBIT_DONE)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    @Test
    @DisplayName("Should do nothing when no stuck transactions found")
    void recover_NoStuckTransactions() {
        when(transactionRepository.findByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        recoveryService.recoverStuckTransactions();

        verify(transactionService, never()).executeFinancialOperations(any(), any(), any(), any(), any());
        verify(transactionService, never()).updateStatus(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Should successfully recover and complete a stuck transaction")
    void recover_SuccessfulRecovery() {
        when(transactionRepository.findByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckTx));

        recoveryService.recoverStuckTransactions();
        verify(transactionService).executeFinancialOperations(
                eq(stuckTx),
                eq(TypeTransaction.TRANSFER),
                eq(1L),
                eq(2L),
                eq(new BigDecimal("100.00"))
        );

        verify(transactionService).updateStatus(eq(999L), eq(Status.COMPLETED), isNull());
    }

    @Test
    @DisplayName("Should mark transaction as FAILED if recovery throws exception")
    void recover_FailedRecovery() {
        when(transactionRepository.findByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckTx));

        doThrow(new RuntimeException("Network error"))
                .when(transactionService).executeFinancialOperations(any(), any(), any(), any(), any());

        recoveryService.recoverStuckTransactions();

        verify(transactionService).updateStatus(
                eq(999L),
                eq(Status.FAILED),
                contains("Recovery failed: Network error")
        );
    }
}
