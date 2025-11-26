package account_service.account_service.dto;

import account_service.account_service.model.Currency;
import account_service.account_service.model.Status;
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
    private Currency currency;
    private BigDecimal balance;
    private Status status;
    private LocalDateTime createAt;
}
