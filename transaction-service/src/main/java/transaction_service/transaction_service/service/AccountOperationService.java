package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.config.AccountClient;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountOperationService {

    private final AccountClient accountClient;


    public void debit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling debit for account {}", txId, accountId);
        accountClient.debit(accountId, amount, txId);
    }

    public void credit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling credit for account {}", txId, accountId);
        accountClient.credit(accountId, amount, txId);
    }

    public void compensate(Long txId, Long accountId, BigDecimal amount) {
        try {
            log.info("TX {} compensation: returning money to account {}", txId, accountId);
            accountClient.credit(accountId, amount, txId);
        } catch (FeignException e) {
            log.error("TX {} compensation FAILED: {}", txId, e.getMessage());
            throw new RuntimeException("Compensation failed", e);
        }
    }
    public AccountResponseDto getAccountById(Long id) {
        log.info("Account founds with id {}", id);
        return accountClient.getAccountById(id);

    }

}
