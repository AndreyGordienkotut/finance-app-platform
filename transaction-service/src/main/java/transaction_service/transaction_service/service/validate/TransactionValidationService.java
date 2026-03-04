package transaction_service.transaction_service.service.validate;

import core.core.dto.AccountResponseDto;
import core.core.enums.StatusAccount;
import core.core.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.WithdrawRequestDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {

    public void validateAccounts(AccountResponseDto from, AccountResponseDto to,
                                  TransactionRequestDto dto) {
        log.debug("TX balance check: balance={}, amount={}", from.getBalance(), dto.getAmount());

        if (dto.getSourceAccountId().equals(dto.getTargetAccountId())) {
            throw new BadRequestException("Same account");
        }

        if (StatusAccount.CLOSED.equals(from.getStatus())
                || StatusAccount.CLOSED.equals(to.getStatus())) {
            throw new BadRequestException("Account is closed");
        }

        if (from.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money");
        }
    }
    public void validateWithdraw(AccountResponseDto source, WithdrawRequestDto dto) {
        log.debug("TX balance check for withdraw: balance={}, amount={}", source.getBalance(), dto.getAmount());
        if (source.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Account is closed.");
        }
        if (source.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money in source account for withdrawal.");
        }
    }
}
