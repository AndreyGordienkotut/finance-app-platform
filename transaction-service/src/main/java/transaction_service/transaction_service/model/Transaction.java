package transaction_service.transaction_service.model;

import core.core.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "transaction", indexes = {
        @Index(name = "idx_tx_user_status_date", columnList = "userId, status, createdAt")
})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(name = "source_account_id")
    private Long sourceAccountId;
    @Column(name="target_account_id")
    private Long targetAccountId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    private Currency currency;
    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    private Status status;
    @Column(nullable = false, name = "create_at")
    private LocalDateTime createdAt;
    @Column(name="error_message")
    private String errorMessage;
    @Column(name = "update_at")
    private LocalDateTime updatedAt;
    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_step", nullable = false)
    private TransactionStep step = TransactionStep.NONE;

    @Column(name = "exchange_rate", nullable = false)
    private BigDecimal exchangeRate;
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @ManyToOne
    @JoinColumn(name = "transaction_category_id")
    private TransactionCategory category;

//    @Version
//    private Long version;


}
