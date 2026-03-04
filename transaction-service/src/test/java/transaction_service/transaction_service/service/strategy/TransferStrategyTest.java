package transaction_service.transaction_service.service.strategy;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import core.core.exception.*;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.service.AccountOperationService;
import transaction_service.transaction_service.service.TransactionStateService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferStrategyTest {

    @Mock
    private AccountOperationService accountOperationService;
    @Mock
    private TransactionStateService transactionStateService;

    @InjectMocks
    private TransferStrategy strategy;

    @Test
    @DisplayName("SAGA SUCCESS: Full execution from NONE → CREDIT_DONE")
    void execute_success_fullSaga() {

        Transaction tx = Transaction.builder()
                .id(1L)
                .step(TransactionStep.NONE)
                .targetAmount(BigDecimal.valueOf(100))
                .build();

        strategy.execute(tx, 1L, 2L, BigDecimal.valueOf(100));

        verify(accountOperationService).debit(1L, 1L, BigDecimal.valueOf(100));
        verify(transactionStateService).updateStep(1L, TransactionStep.DEBIT_DONE);
        verify(accountOperationService).credit(1L, 2L, BigDecimal.valueOf(100));
        verify(transactionStateService).updateStep(1L, TransactionStep.CREDIT_DONE);
    }

    @Test
    @DisplayName("SAGA RESUME: Skip debit if step = DEBIT_DONE")
    void execute_resumeFromDebitDone() {

        Transaction tx = Transaction.builder()
                .id(1L)
                .step(TransactionStep.DEBIT_DONE)
                .targetAmount(BigDecimal.valueOf(100))
                .build();

        strategy.execute(tx, 1L, 2L, BigDecimal.valueOf(100));

        verify(accountOperationService, never()).debit(any(), any(), any());
        verify(accountOperationService).credit(1L, 2L, BigDecimal.valueOf(100));
        verify(transactionStateService).updateStep(1L, TransactionStep.CREDIT_DONE);
    }

    @Test
    @DisplayName("SAGA FAILURE: Debit throws ConflictException → rethrow")
    void execute_debitConflict_rethrow() {

        Transaction tx = Transaction.builder()
                .id(1L)
                .step(TransactionStep.NONE)
                .build();

        doThrow(new ConflictException("Lock"))
                .when(accountOperationService)
                .debit(any(), any(), any());

        assertThrows(ConflictException.class, () ->
                strategy.execute(tx, 1L, 2L, BigDecimal.TEN)
        );
    }

    @Test
    @DisplayName("SAGA FAILURE: Credit fails → compensation success")
    void execute_creditFails_compensationSuccess() {

        Transaction tx = Transaction.builder()
                .id(1L)
                .step(TransactionStep.NONE)
                .targetAmount(BigDecimal.TEN)
                .build();

        doThrow(new RuntimeException("Credit failed"))
                .when(accountOperationService)
                .credit(any(), any(), any());

        assertThrows(BadRequestException.class, () ->
                strategy.execute(tx, 1L, 2L, BigDecimal.TEN)
        );

        verify(accountOperationService)
                .compensate(1L, 1L, BigDecimal.TEN);
    }

    @Test
    @DisplayName("SAGA FAILURE: Credit fails → compensation fails → updateStatus")
    void execute_compensationFails() {

        Transaction tx = Transaction.builder()
                .id(1L)
                .step(TransactionStep.NONE)
                .targetAmount(BigDecimal.TEN)
                .build();

        doThrow(new RuntimeException("Credit failed"))
                .when(accountOperationService)
                .credit(any(), any(), any());

        doThrow(new RuntimeException("Rollback failed"))
                .when(accountOperationService)
                .compensate(any(), any(), any());

        assertThrows(BadRequestException.class, () ->
                strategy.execute(tx, 1L, 2L, BigDecimal.TEN)
        );

        verify(transactionStateService)
                .updateStatus(eq(1L), eq(Status.FAILED), contains("Compensation failed"));
    }

}