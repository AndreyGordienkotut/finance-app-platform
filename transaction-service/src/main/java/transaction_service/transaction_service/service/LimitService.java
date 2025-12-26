package transaction_service.transaction_service.service;
import core.core.dto.AccountResponseDto;
import core.core.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.dto.LimitResponseDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionLimit;
import transaction_service.transaction_service.repository.TransactionLimitRepository;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitService {
    private final TransactionLimitRepository transactionLimitRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("5000");
    private static final BigDecimal DEFAULT_SINGLE_LIMIT = new BigDecimal("1000");
    @Transactional
    public void checkTransactionLimit(Long userId, BigDecimal amount) {
        TransactionLimit limit = transactionLimitRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultLimit(userId));
        if(amount.compareTo(limit.getSingleLimit())>0){
            throw new LimitExceededException("Transaction amount exceeds single limit of " + limit.getSingleLimit());
        }
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        BigDecimal spentInLast24h = transactionRepository.calculateTotalSpentForUserInLast24Hours(userId, twentyFourHoursAgo);

        if (spentInLast24h == null) spentInLast24h = BigDecimal.ZERO;

        if (spentInLast24h.add(amount).compareTo(limit.getDailyLimit()) > 0) {
            throw new LimitExceededException("Daily limit exceeded. You already spent " + spentInLast24h +
                    " in last 24h. Limit is " + limit.getDailyLimit());
        }
    }
    @Transactional(readOnly = true)
    public LimitResponseDto getLimits(Long userId) {
        TransactionLimit limit = transactionLimitRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultLimit(userId));
        return convertToDto(limit);
    }

    @Transactional
    public LimitResponseDto updateLimits(Long userId, BigDecimal daily, BigDecimal single) {
        TransactionLimit limit = transactionLimitRepository.findByUserId(userId)
                .orElse(createDefaultLimit(userId));

        limit.setDailyLimit(daily);
        limit.setSingleLimit(single);

        TransactionLimit savedLimit = transactionLimitRepository.save(limit);
        return convertToDto(savedLimit);
    }
    private TransactionLimit createDefaultLimit(Long userId) {
        TransactionLimit newLimit = new TransactionLimit();
        newLimit.setUserId(userId);
        newLimit.setDailyLimit(DEFAULT_DAILY_LIMIT);
        newLimit.setSingleLimit(DEFAULT_SINGLE_LIMIT);
        return transactionLimitRepository.save(newLimit);
    }
    private LimitResponseDto convertToDto(TransactionLimit limit) {
        return new LimitResponseDto(
              limit.getDailyLimit(),
                limit.getSingleLimit()
        );
    }

}
