package account_service.account_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppliedTransactions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false,name = "transaction_id")
    private Long transactionId;
    @ManyToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id", nullable = false)
    private Account account;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
