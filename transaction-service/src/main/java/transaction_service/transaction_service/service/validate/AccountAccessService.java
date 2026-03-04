package transaction_service.transaction_service.service.validate;

import core.core.dto.AccountResponseDto;
import core.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.config.AccountClient;

@Service
@RequiredArgsConstructor

public class AccountAccessService {
    private final AccountClient accountClient;
    public AccountResponseDto validateAccountOwnership(Long accountId, Long userId) {

        AccountResponseDto account = accountClient.getAccountById(accountId);
        if (!account.getUserId().equals(userId)) {
            throw new NotFoundException("Account not found or access denied.");
        }
        return account;
    }
}
