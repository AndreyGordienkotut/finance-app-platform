package transaction_service.transaction_service.service.strategy;

import core.core.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionStep;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.TransactionService;
import core.core.exception.*;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferStrategy implements FinancialOperationStrategy {

    private final TransactionService transactionService;

    @Override
    public TransactionType getType() {
        return TransactionType.TRANSFER;
    }

    @Override
    public void execute(Transaction tx,
                        Long sourceAccountId,
                        Long targetAccountId,
                        BigDecimal amount) {

        boolean debitSucceeded = tx.getStep() == TransactionStep.DEBIT_DONE;

        try {

            if (tx.getStep() == TransactionStep.NONE) {
                log.info("TX {} SAGA: Debit {}", tx.getId(), amount);

                transactionService.executeDebit(tx.getId(), sourceAccountId, amount);
                transactionService.updateStep(tx.getId(), TransactionStep.DEBIT_DONE);
                tx.setStep(TransactionStep.DEBIT_DONE);
                debitSucceeded = true;
            }

            if (tx.getStep() == TransactionStep.DEBIT_DONE) {
                log.info("TX {} SAGA: Credit {}", tx.getId(), tx.getTargetAmount());

                transactionService.executeCredit(tx.getId(), targetAccountId, tx.getTargetAmount());
                transactionService.updateStep(tx.getId(), TransactionStep.CREDIT_DONE);
            }

        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {

            if (debitSucceeded) {
                try {
                    transactionService.compensate(tx.getId(), sourceAccountId, amount);
                } catch (RuntimeException re) {
                    transactionService.updateStatus(
                            tx.getId(),
                            Status.FAILED,
                            "Compensation failed: " + re.getMessage()
                    );
                    throw new BadRequestException("Transfer failed. Compensation failed.");
                }
            }

            throw new BadRequestException("Transfer failed: " + e.getMessage());
        }
    }
}