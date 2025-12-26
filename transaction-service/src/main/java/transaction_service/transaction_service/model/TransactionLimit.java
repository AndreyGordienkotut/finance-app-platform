package transaction_service.transaction_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "transaction_limit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLimit {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(name = "daily_limit",nullable = false)
    private BigDecimal dailyLimit;

    @Column(name = "single_limit",nullable = false)
    private BigDecimal singleLimit;
}
