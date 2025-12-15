package account_service.account_service.service;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.model.AppliedTransactions;
import account_service.account_service.repository.AppliedTransactionRepository;
import core.core.dto.AccountResponseDto;
import core.core.exception.*;
import account_service.account_service.model.Account;
import core.core.enums.StatusAccount;
import account_service.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final AppliedTransactionRepository appliedTransactionRepository;
    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto accountRequestDto, Long userId) {
        long count = accountRepository.countByUserIdAndStatusAccount(userId, StatusAccount.ACTIVE);

        if (count >= 10) {
            throw new BadRequestException("You have reached the limit for creating accounts.");
        }

        Account account = Account.builder()
                .userId(userId)
                .currency(accountRequestDto.getCurrency())
                .balance(BigDecimal.ZERO)
                .statusAccount(StatusAccount.ACTIVE)
                .createAt(LocalDateTime.now())
                .build();
        accountRepository.save(account);
        return convertToDto(account);
    }
    public Page<AccountResponseDto> getAllAccounts(Long userId,Pageable pageable) {
        Page<Account> accounts = accountRepository.findAllByUserIdAndStatusAccountNotOrderByCreateAtAsc(userId, StatusAccount.CLOSED, pageable);
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
        account.setStatusAccount(StatusAccount.CLOSED);
        accountRepository.save(account);
        return convertToDto(account);
    }

    //accountClient
    @Transactional(readOnly = true)
    public AccountResponseDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Account not found"));

        return AccountResponseDto.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .status(account.getStatusAccount())
                .createAt(account.getCreateAt())
                .build();
    }
    @Transactional
    public void debit( Long accountId, BigDecimal amount,Long transactionId) {
        if (appliedTransactionRepository.existsByTransactionIdAndAccountId(transactionId, accountId)) {
            return;
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Account not found"));

        if (account.getStatusAccount() != StatusAccount.ACTIVE) {
            throw new BadRequestException("Account is not active");
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient funds");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        appliedTransactionRepository.save(
                AppliedTransactions.builder()
                        .transactionId(transactionId)
                        .account(account)
                        .amount(amount)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Transactional
    public void credit(Long accountId, BigDecimal amount, Long transactionId) {
        if (appliedTransactionRepository.existsByTransactionIdAndAccountId(transactionId, accountId)) {
            return;
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Account not found"));

        if (account.getStatusAccount() != StatusAccount.ACTIVE) {
            throw new BadRequestException("Account is not active");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        appliedTransactionRepository.save(
                AppliedTransactions.builder()
                        .transactionId(transactionId)
                        .account(account)
                        .amount(amount)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }


    private AccountResponseDto convertToDto(Account account) {
        return new AccountResponseDto(
                account.getId(),
                account.getUserId(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatusAccount(),
                account.getCreateAt()
        );
    }
}