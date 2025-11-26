package account_service.account_service.service;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.dto.AccountResponseDto;
import account_service.account_service.exception.NotFoundException;
import account_service.account_service.model.Account;
import account_service.account_service.model.Currency;
import account_service.account_service.model.Status;
import account_service.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import account_service.account_service.exception.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto accountRequestDto,Long userId) {
        long count = accountRepository.countByUserIdAndStatus(userId, Status.ACTIVE);

        if (count >= 10) {
            throw new BadRequestException("You have reached the limit for creating accounts.");
        }

        Account account = Account.builder()
                .userId(userId)
                .currency(accountRequestDto.getCurrency())
                .balance(BigDecimal.ZERO)
                .status(Status.ACTIVE)
                .createAt(LocalDateTime.now())
                .build();
        accountRepository.save(account);
        return convertToDto(account);
    }
    public Page<AccountResponseDto> getAllAccounts(Long userId,Pageable pageable) {
        Page<Account> accounts = accountRepository.findAllByUserIdAndStatusNotOrderByCreateAtAsc(userId, Status.CLOSED, pageable);
        return accounts.map(this::convertToDto);

    }

    @Transactional
    public AccountResponseDto closeAccount(Long accountId,Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("Account with id "+accountId+" is not yours");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("Account balance must be zero to close.");
        }
        account.setStatus(Status.CLOSED);
        accountRepository.save(account);
        return convertToDto(account);
    }
    private AccountResponseDto convertToDto(Account account) {
        return new AccountResponseDto(
                account.getId(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreateAt()
        );
    }
}