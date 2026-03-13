package transaction_service.transaction_service.dto.bulk;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkTransferRequestDto {
    @NotNull
    private Long sourceAccountId;
    @NotEmpty
    @Size(min=1, max=10)
    private List<BulkTransferItemDto> transfers;
}
