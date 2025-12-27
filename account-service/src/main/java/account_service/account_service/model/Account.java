package account_service.account_service.model;

import core.core.enums.Currency;
import core.core.enums.StatusAccount;
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
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Enumerated(EnumType.STRING)
    private Currency currency;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_account")
    private StatusAccount statusAccount;
    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;
}
