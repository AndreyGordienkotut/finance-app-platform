package transaction_service.transaction_service.service.validate;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.exception.FraudDetectedException;
import core.core.exception.InternalServerErrorException;
import core.core.exception.LimitExceededException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.dto.ValidationResult;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.AccountOperationService;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.LimitService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ParallelValidationService {

    private final LimitService limitService;
    private final ExchangeRateService exchangeRateService;
    private final FraudValidationService fraudValidationService;
    private final AccountOperationService accountOperationService;
    private final Executor transactionValidationExecutor;

    public ParallelValidationService(
            LimitService limitService,
            ExchangeRateService exchangeRateService,
            FraudValidationService fraudValidationService,
            AccountOperationService accountOperationService,
            @Qualifier("transactionValidationExecutor") Executor transactionValidationExecutor
    ) {
        this.limitService = limitService;
        this.exchangeRateService = exchangeRateService;
        this.fraudValidationService = fraudValidationService;
        this.accountOperationService = accountOperationService;
        this.transactionValidationExecutor = transactionValidationExecutor;
    }

    public ValidationResult validate(Long userId, BigDecimal amount, Currency currency,
                                     Long targetAccountId, TransactionType type,
                                     Instant accountCreatedAt) {

        BigDecimal rate = resolveRate(currency, targetAccountId, type);
        BigDecimal targetAmount = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountForLimit = type == TransactionType.TRANSFER ? targetAmount : amount;

        CompletableFuture<Void> limitFuture = CompletableFuture.runAsync(
                () -> limitService.checkTransactionLimit(userId, amountForLimit),
                transactionValidationExecutor
        );

        CompletableFuture<Void> fraudFuture = CompletableFuture.runAsync(
                () -> fraudValidationService.validate(userId, amount, accountCreatedAt),
                transactionValidationExecutor
        );

        log.info("Running parallel validation for user {}: limit + fraud", userId);

        try {
            CompletableFuture.allOf(limitFuture, fraudFuture).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            log.warn("Parallel validation failed for user {}: {}", userId, cause.getMessage());
            if (cause instanceof LimitExceededException ex) throw ex;
            if (cause instanceof FraudDetectedException ex) throw ex;
            throw new InternalServerErrorException("Validation failed: " + cause.getMessage());
        }

        log.info("Parallel validation passed for user {}", userId);
        return ValidationResult.builder()
                .rate(rate)
                .targetAmount(targetAmount)
                .build();
    }

    private BigDecimal resolveRate(Currency currency, Long targetAccountId, TransactionType type) {
        if (type == TransactionType.TRANSFER && targetAccountId != null) {
            AccountResponseDto targetAcc = accountOperationService.getAccountById(targetAccountId);
            if (!currency.equals(targetAcc.getCurrency())) {
                return exchangeRateService.getRate(currency, targetAcc.getCurrency());
            }
        }
        return BigDecimal.ONE;
    }
}