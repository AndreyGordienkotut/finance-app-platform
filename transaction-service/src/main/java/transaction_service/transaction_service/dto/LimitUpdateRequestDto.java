package transaction_service.transaction_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LimitUpdateRequestDto {
    @NotNull
    @Positive
    private BigDecimal dailyLimit;
    @NotNull
    @Positive
    private BigDecimal singleLimit;
}
