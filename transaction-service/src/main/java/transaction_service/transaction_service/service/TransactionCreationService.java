package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCreationService {
    private final TransactionRepository transactionRepository;
    private final AccountOperationService accountOperationService;
    private final ExchangeRateService exchangeRateService;
    private final LimitService limitService;


    @Transactional
    public Transaction createTransaction(Long sourceId, Long targetId, BigDecimal amount,
                                         Currency currency, TransactionType type, String idempotencyKey, Long userId, TransactionCategory category) {


        BigDecimal rate = BigDecimal.ONE;
        BigDecimal targetAmount = amount;
        if (type == TransactionType.TRANSFER && targetId != null) {
            AccountResponseDto targetAcc = accountOperationService.getAccountById(targetId);
            if (!currency.equals(targetAcc.getCurrency())) {
                rate = exchangeRateService.getRate(currency, targetAcc.getCurrency());
                targetAmount = exchangeRateService.convert(amount, rate);
            }
        }
        Transaction tx = Transaction.builder()
                .userId(userId)
                .sourceAccountId(sourceId)
                .targetAccountId(targetId)
                .amount(amount)
                .targetAmount(targetAmount)
                .exchangeRate(rate)
                .status(Status.CREATED)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .transactionType(type)
                .step(TransactionStep.NONE)
                .category(category)
                .build();

        BigDecimal limitAmount = type == TransactionType.TRANSFER ? targetAmount : amount;
        limitService.checkTransactionLimit(userId, limitAmount);
        Transaction saved = transactionRepository.save(tx);
        log.info("TX {} created (Type: {})", saved.getId(), type);
        return saved;
    }
}
