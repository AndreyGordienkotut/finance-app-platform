package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeRateResponseDto {
    private String base;
    private Map<String, BigDecimal> rates;
}
