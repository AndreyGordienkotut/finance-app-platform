package transaction_service.transaction_service.service.strategy;

import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionType;

import java.math.BigDecimal;

public interface FinancialOperationStrategy {
    TransactionType getType();
    void execute(Transaction tx,
                 Long sourceAccountId,
                 Long targetAccountId,
                 BigDecimal amount);

}