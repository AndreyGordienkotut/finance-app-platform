package transaction_service.transaction_service.service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.AccountOperationService;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepositStrategy implements FinancialOperationStrategy {

    private final AccountOperationService accountOperationService;

    @Override
    public TransactionType getType() {
        return TransactionType.DEPOSIT;
    }

    @Override
    public void execute(Transaction tx,
                        Long sourceAccountId,
                        Long targetAccountId,
                        BigDecimal amount) {

        accountOperationService.credit(tx.getId(), targetAccountId, amount);
    }
}