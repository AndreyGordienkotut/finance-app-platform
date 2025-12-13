package transaction_service.transaction_service.model;

import core.core.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_account_id")
    private Long fromAccountId;
    @Column(name="to_account_id")
    private Long toAccountId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Currency currency;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(nullable = false, name = "create_at")
    private LocalDateTime createdAt;
    @Column(name="error_message")
    private String errorMessage;
    @Column(name = "update_at")
    private LocalDateTime updatedAt;
    @Enumerated(EnumType.STRING)
    @Column(name="roll_back_status", nullable = false)
    private RollBackStatus rollbackStatus = RollBackStatus.NONE;
    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;


}
