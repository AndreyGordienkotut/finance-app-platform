package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import core.core.enums.Currency;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;


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
    private Instant createdAt;
    private String error;
    private Instant updatedAt;
    private TransactionType transactionType;
    private TransactionCategory category;

}
