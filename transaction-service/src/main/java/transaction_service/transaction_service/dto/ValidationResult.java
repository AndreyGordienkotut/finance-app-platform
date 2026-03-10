package transaction_service.transaction_service.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ValidationResult {
    BigDecimal rate;
    BigDecimal targetAmount;
}