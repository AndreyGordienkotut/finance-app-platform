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
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCreationService {
    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction createTransaction(Long sourceId, Long targetId, BigDecimal amount,
                                         Currency currency, TransactionType type, String idempotencyKey, Long userId, TransactionCategory category,BigDecimal rate, BigDecimal targetAmount) {

        Transaction tx = Transaction.builder()
                .userId(userId)
                .sourceAccountId(sourceId)
                .targetAccountId(targetId)
                .amount(amount)
                .targetAmount(targetAmount)
                .exchangeRate(rate)
                .status(Status.CREATED)
                .currency(currency)
                .createdAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .transactionType(type)
                .step(TransactionStep.NONE)
                .category(category)
                .build();

        Transaction saved = transactionRepository.save(tx);
        log.info("TX {} created (Type: {}, Rate: {})", saved.getId(), type, rate);
        return saved;
    }
}
