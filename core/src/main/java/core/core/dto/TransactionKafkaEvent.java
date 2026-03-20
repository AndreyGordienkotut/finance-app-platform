package core.core.dto;

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
public class TransactionKafkaEvent {
    private Long transactionId;
    private Long userId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private BigDecimal amount;
    private BigDecimal targetAmount;
    private BigDecimal exchangeRate;
    private String currency;
    private String transactionType;
    private String categoryName;
    private Instant createdAt;
}