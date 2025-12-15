package core.core.dto;

import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponseDto {
    private Long id;
    private Long userId;
    private Currency currency;
    private BigDecimal balance;
    private StatusAccount status;
    private LocalDateTime createAt;
}
