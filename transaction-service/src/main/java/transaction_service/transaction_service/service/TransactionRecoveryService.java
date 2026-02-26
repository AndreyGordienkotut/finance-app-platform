package transaction_service.transaction_service.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionRecoveryService {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private static final long PROCESSING_TIMEOUT_MINUTES = 5;
    private final Map<TransactionType, FinancialOperationStrategy> strategies;
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void recoverStuckTransactions() {
        log.info("Starting recovery process for stuck transactions...");

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES);

        List<Transaction> stuckTransactions = transactionRepository
                .findByStatusAndUpdatedAtBefore(Status.PROCESSING, timeoutThreshold);

        if (stuckTransactions.isEmpty()) {
            log.info("No stuck transactions found.");
            return;
        }

        log.warn("Found {} stuck transactions (PROCESSING > {} mins). Attempting recovery.",
                stuckTransactions.size(), PROCESSING_TIMEOUT_MINUTES);

        for (Transaction tx : stuckTransactions) {
            try {
                log.info("TX {} attempting retry (Type: {}, Step: {})", tx.getId(), tx.getTransactionType(), tx.getStep());
                strategies.get(tx.getTransactionType())
                        .execute(
                                tx,
                                tx.getSourceAccountId(),
                                tx.getTargetAccountId(),
                                tx.getAmount()
                        );
                transactionService.updateStatus(tx.getId(), Status.COMPLETED, null);
                log.info("TX {} successfully recovered and set to COMPLETED.", tx.getId());

            } catch (Exception e) {
                String errorMsg = "Recovery failed: " + e.getMessage();
                log.error("TX {} final recovery FAILED. Marking FAILED. Error: {}", tx.getId(), errorMsg);
                transactionService.updateStatus(tx.getId(), Status.FAILED, errorMsg);
            }
        }
        log.info("Recovery process finished.");
    }
}
