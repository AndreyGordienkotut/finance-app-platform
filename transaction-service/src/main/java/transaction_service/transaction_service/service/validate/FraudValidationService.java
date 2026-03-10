package transaction_service.transaction_service.service.validate;

import core.core.exception.FraudDetectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudValidationService {
    private final TransactionRepository transactionRepository;
    private static final BigDecimal SUSPICIOUS_AMOUNT = new BigDecimal("10000");
    private static final int MAX_TRANSACTIONS_PER_5_MIN = 5;
    public void validate(Long userId, BigDecimal amount, Instant accountCreatedAt) {

        checkSuspiciousAmount(userId, amount);
        checkVelocity(userId);
        checkNewAccount(userId, amount, accountCreatedAt);
        log.info("Fraud check passed for user {}", userId);
    }

    private void checkSuspiciousAmount(Long userId, BigDecimal amount) {
        if (amount.compareTo(SUSPICIOUS_AMOUNT) > 0) {
            log.warn("Fraud: suspicious amount {} for user {}", amount, userId);
            throw new FraudDetectedException("Transaction amount is suspiciously large");
        }
    }

    private void checkVelocity(Long userId) {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);

        long recentCount = transactionRepository
                .countByUserIdAndCreatedAtAfter(userId, fiveMinutesAgo);

        if (recentCount >= MAX_TRANSACTIONS_PER_5_MIN) {
            log.warn("Fraud: velocity check failed for user {}. Count: {}", userId, recentCount);
            throw new FraudDetectedException("Too many transactions in short period");
        }
    }

    private void checkNewAccount(Long userId, BigDecimal amount, Instant accountCreatedAt) {
        if (accountCreatedAt == null) return;

        long accountAgeDays = ChronoUnit.DAYS.between(accountCreatedAt, Instant.now());

        if (accountAgeDays < 7 && amount.compareTo(new BigDecimal("1000")) > 0) {
            log.warn("Fraud: new account {} trying large transfer", userId);
            throw new FraudDetectedException("New account cannot perform large transactions");
        }
    }
}
