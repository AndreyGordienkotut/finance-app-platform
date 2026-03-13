package transaction_service.transaction_service.dto.bulk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import transaction_service.transaction_service.dto.TransactionResponseDto;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransferResponseDto {
    private List<TransactionResponseDto> successful;
    private List<BulkTransferFailedItemDto> failed;
    private int totalProcessed;
    private int successCount;
    private int failedCount;

}
