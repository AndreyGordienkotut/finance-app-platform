package transaction_service.transaction_service.service.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.AccountOperationService;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class WithdrawStrategy implements FinancialOperationStrategy {

    private final AccountOperationService accountOperationService;

    @Override
    public TransactionType getType() {
        return TransactionType.WITHDRAW;
    }

    @Override
    public void execute(Transaction tx,
                        Long sourceAccountId,
                        Long targetAccountId,
                        BigDecimal amount) {

        accountOperationService.debit(tx.getId(), sourceAccountId, amount);
    }
}