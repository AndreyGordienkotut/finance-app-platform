package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryResponse {
    private String summary;
    private BigDecimal totalSpent;
    private Instant analyzedFrom;
    private Instant analyzedTo;
}