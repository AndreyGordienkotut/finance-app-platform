package transaction_service.transaction_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class TransactionRecoveryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private FinancialOperationStrategy transferStrategy;
    @InjectMocks
    private TransactionRecoveryService recoveryService;
    private Map<TransactionType, FinancialOperationStrategy> strategies;

    private Transaction stuckTx;

    @BeforeEach
    void setUp() {
        stuckTx = Transaction.builder()
                .id(999L)
                .status(Status.PROCESSING)
                .transactionType(TransactionType.TRANSFER)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(new BigDecimal("100.00"))
                .step(TransactionStep.DEBIT_DONE)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        strategies = Map.of(TransactionType.TRANSFER, transferStrategy);
        ReflectionTestUtils.setField(recoveryService, "strategies", strategies);
    }

    @Test
    @DisplayName("Should do nothing when no stuck transactions found")
    void recover_NoStuckTransactions() {
        when(transactionRepository.findByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        recoveryService.recoverStuckTransactions();

        verifyNoInteractions(transferStrategy);
        verify(transactionService, never()).updateStatus(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Should successfully recover and complete a stuck transaction")
    void recover_SuccessfulRecovery() {
        when(transactionRepository.findByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckTx));

        recoveryService.recoverStuckTransactions();
        verify(transferStrategy).execute(
                eq(stuckTx),
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
                .when(transferStrategy).execute(any(), any(), any(), any());

        recoveryService.recoverStuckTransactions();

        verify(transactionService).updateStatus(
                eq(999L),
                eq(Status.FAILED),
                contains("Recovery failed: Network error")
        );
    }
}
