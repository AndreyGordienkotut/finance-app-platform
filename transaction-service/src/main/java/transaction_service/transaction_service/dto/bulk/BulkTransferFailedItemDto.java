package transaction_service.transaction_service.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransferFailedItemDto {
    private Long targetAccountId;
    private BigDecimal amount;
    private String reason;
}
