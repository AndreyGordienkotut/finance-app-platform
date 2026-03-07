package account_service.account_service.model;

import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.BadRequestException;
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

    public void close(Long requestUserId) {
        if (!this.userId.equals(requestUserId)) {
            throw new BadRequestException("Account with id " + id + " is not yours");
        }
        if (this.balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("Account balance must be zero to close.");
        }
        this.statusAccount = StatusAccount.CLOSED;
    }

    public void debit(BigDecimal amount) {
        validateAmountPositive(amount);
        ensureActive();
        if (this.balance.compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient funds");
        }
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        validateAmountPositive(amount);
        ensureActive();
        this.balance = this.balance.add(amount);
    }

    private void ensureActive() {
        if (this.statusAccount != StatusAccount.ACTIVE) {
            throw new BadRequestException("Account is not active");
        }
    }

    private void validateAmountPositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
    }
}
