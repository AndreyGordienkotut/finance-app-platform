package transaction_service.transaction_service.dto.bulk;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransferItemDto {

    @NotNull
    private Long targetAccountId;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "10000.00")
    private BigDecimal amount;

    private Long categoryId;
}