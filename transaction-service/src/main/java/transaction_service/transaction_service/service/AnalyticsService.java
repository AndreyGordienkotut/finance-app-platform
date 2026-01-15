package transaction_service.transaction_service.service;

import core.core.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.dto.TimelineResponse;
import transaction_service.transaction_service.dto.TopCategoryResponse;
import transaction_service.transaction_service.dto.TotalSpentResponse;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TransactionRepository transactionRepository;

    @Cacheable(value = "totalSpent", key = "'total:' + #userId + ':' + #from + ':' + #to", cacheManager = "cacheManager")
    public TotalSpentResponse getTotalSpent(Long userId, LocalDateTime from, LocalDateTime to) {
        log.info("Calculating total spent for user {} (cache miss)", userId);
        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        BigDecimal total = transactionRepository.getTotalSpent(userId, Status.COMPLETED, dates[0], dates[1]);

        return new TotalSpentResponse(total != null ? total : BigDecimal.ZERO, "MIXED_CURRENCY");
    }
    @Cacheable(value = "topCategories", key = "'top:' + #userId + ':' + #from + ':' + #to + ':' + #limit", cacheManager = "cacheManager")
    public List<TopCategoryResponse> getTopCategories(Long userId, LocalDateTime from, LocalDateTime to, int limit) {
        log.info("Calculating top categories for user {} (cache miss)", userId);
        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        Pageable pageable = PageRequest.of(0, limit);
        List<Object[]> results = transactionRepository.getTopCategories(userId, Status.COMPLETED, dates[0], dates[1], pageable);

        return results.stream()
                .map(r -> new TopCategoryResponse((Long) r[0], (String) r[1], (BigDecimal) r[2]))
                .toList();
    }


    private LocalDateTime[] validateAndNormalizeDates(LocalDateTime from, LocalDateTime to) {
        if (from == null) from = LocalDateTime.now().minusDays(30);
        if (to == null) to = LocalDateTime.now();
        if (from.isAfter(to)){
            throw new BadRequestException("Start date must be before end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > 365){
            throw new BadRequestException("Date range cannot exceed 1 year");
        }
        return new LocalDateTime[]{from, to};
    }
}
