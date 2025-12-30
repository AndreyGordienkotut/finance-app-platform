package transaction_service.transaction_service.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequestDto {
    @NotNull
    private Long sourceAccountId;
    @NotNull
    private Long targetAccountId;
    @DecimalMin(value = "0.01", inclusive = true)
    @DecimalMax(value = "10000.00")
    @NotNull
    private BigDecimal amount;
    private Long categoryId;

}

