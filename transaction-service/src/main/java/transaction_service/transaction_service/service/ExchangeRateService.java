package transaction_service.transaction_service.service;

import core.core.enums.Currency;
import core.core.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.config.ExchangeRateClient;
import transaction_service.transaction_service.dto.ExchangeRateResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {
    private final ExchangeRateClient exchangeRateClient;


    @Cacheable(value = "exchangeRates", key = "#from.name() + '-' + #to.name()")
    public BigDecimal getRate(Currency from, Currency to) {
        if (from == to) return BigDecimal.ONE;

        try {
            log.info("Fetching real rate from API for {} -> {}", from, to);
            ExchangeRateResponseDto response = exchangeRateClient.getLatestRate(from.name(), to.name());

            BigDecimal rate = response.getRates().get(to.name());
            if (rate == null) throw new RuntimeException("Rate not found in response");

            return rate;
        } catch (Exception e) {
            log.error("Failed to fetch rate: {}", e.getMessage());
            throw new ExternalServiceException("Exchange service unavailable");
        }
    }

    public BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

}
