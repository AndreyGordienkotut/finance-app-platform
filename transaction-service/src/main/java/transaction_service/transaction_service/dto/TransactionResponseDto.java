package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import core.core.Currency;
import transaction_service.transaction_service.model.RollBackStatus;
import transaction_service.transaction_service.model.Status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDto {
    private Long id;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private Currency currency;
    private Status status;
    private LocalDateTime createdAt;
    private String error;
    private LocalDateTime updatedAt;
    private RollBackStatus rollbackStatus;

}
