package transaction_service.transaction_service.service;


import core.core.enums.Currency;
import core.core.exception.ExternalServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.config.ExchangeRateClient;
import transaction_service.transaction_service.dto.ExchangeRateResponseDto;

import java.math.BigDecimal;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExchangeRateServiceTest {
    @Mock
    private ExchangeRateClient exchangeRateClient;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    //getRate
    @Test
    @DisplayName("Should return 1.0 when from and to currencies are the same")
    void getRate_SameCurrency_ReturnsOne() {
        Currency usd = Currency.USD;

        BigDecimal rate = exchangeRateService.getRate(usd, usd);

        assertEquals(0, BigDecimal.ONE.compareTo(rate));
        verifyNoInteractions(exchangeRateClient);
    }

    @Test
    @DisplayName("Should return correct rate from API")
    void getRate_Success() {
        Currency from = Currency.USD;
        Currency to = Currency.EUR;
        BigDecimal expectedRate = new BigDecimal("0.92");

        ExchangeRateResponseDto mockResponse = new ExchangeRateResponseDto();
        mockResponse.setRates(Map.of("EUR", expectedRate));

        when(exchangeRateClient.getLatestRate("USD", "EUR")).thenReturn(mockResponse);

        BigDecimal actualRate = exchangeRateService.getRate(from, to);

        assertEquals(expectedRate, actualRate);
        verify(exchangeRateClient).getLatestRate("USD", "EUR");
    }

    @Test
    @DisplayName("Should throw ExternalServiceException when rate is missing in response")
    void getRate_RateNotFound_ThrowsExternalServiceException() {
        ExchangeRateResponseDto emptyResponse = new ExchangeRateResponseDto();
        emptyResponse.setRates(Map.of());

        when(exchangeRateClient.getLatestRate(anyString(), anyString())).thenReturn(emptyResponse);

        assertThrows(ExternalServiceException.class, () ->
                exchangeRateService.getRate(Currency.USD, Currency.EUR));
    }

    @Test
    @DisplayName("Should throw ExternalServiceException when Feign client fails")
    void getRate_ClientError_ThrowsExternalServiceException() {
        when(exchangeRateClient.getLatestRate(anyString(), anyString()))
                .thenThrow(new RuntimeException("API Down"));

        assertThrows(ExternalServiceException.class, () ->
                exchangeRateService.getRate(Currency.USD, Currency.EUR));
    }
    //convert
    @Test
    @DisplayName("Should correctly multiply amount by rate")
    void convert_MultipliesCorrectly() {
        BigDecimal amount = new BigDecimal("100");
        BigDecimal rate = new BigDecimal("0.85");

        BigDecimal result = exchangeRateService.convert(amount, rate);

        // 100 * 0.85 = 85.00
        assertEquals(0, new BigDecimal("85.00").compareTo(result));
    }

    @Test
    @DisplayName("Should round HALF_UP with scale 2")
    void convert_RoundingHalfUp() {
        BigDecimal amount = new BigDecimal("10.00");
        // 10 * 1.555 = 15.550 -> 15.55
        // 10 * 1.556 = 15.560 -> 15.56
        BigDecimal rateUp = new BigDecimal("1.556");
        BigDecimal rateDown = new BigDecimal("1.554");

        BigDecimal resultUp = exchangeRateService.convert(amount, rateUp);
        BigDecimal resultDown = exchangeRateService.convert(amount, rateDown);

        assertEquals(new BigDecimal("15.56"), resultUp);
        assertEquals(new BigDecimal("15.54"), resultDown);
    }

}
