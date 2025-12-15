package transaction_service.transaction_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawRequestDto {
    @NotNull
    private Long sourceAccountId;
    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    @DecimalMax(value = "10000.00")
    private BigDecimal amount;
}
