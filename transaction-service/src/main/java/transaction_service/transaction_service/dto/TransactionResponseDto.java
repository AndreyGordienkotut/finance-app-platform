package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import core.core.enums.Currency;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.model.TypeTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDto {
    private Long id;
    private Long sourceAccountId;
    private Long targetAccountId;
    private BigDecimal amount;
    private BigDecimal targetAmount;
    private BigDecimal exchangeRate;
    private Currency currency;
    private Status status;
    private LocalDateTime createdAt;
    private String error;
    private LocalDateTime updatedAt;
    private TypeTransaction typeTransaction;
    private TransactionCategory category;

}
