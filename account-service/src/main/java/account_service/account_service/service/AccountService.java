package account_service.account_service.service;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.mapper.AccountMapper;
import account_service.account_service.model.AppliedTransactions;
import account_service.account_service.repository.AppliedTransactionRepository;
import core.core.dto.AccountResponseDto;
import core.core.exception.*;
import account_service.account_service.model.Account;
import core.core.enums.StatusAccount;
import account_service.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private final AppliedTransactionRepository appliedTransactionRepository;

    private final AccountMapper accountMapper;
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
                .createAt(Instant.now())
                .build();
        accountRepository.save(account);
        return accountMapper.toDto(account);
    }
    public Page<AccountResponseDto> getAllAccounts(Long userId,Pageable pageable) {
        Page<Account> accounts = accountRepository.findAllByUserIdAndStatusAccountNotOrderByCreateAtAsc(userId, StatusAccount.CLOSED, pageable);
        return accounts.map(accountMapper::toDto);

    }

    @Transactional
    public AccountResponseDto closeAccount(Long accountId,Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        account.close(userId);
        accountRepository.save(account);
        return accountMapper.toDto(account);
    }

    //accountClient
    @Transactional(readOnly = true)
    public AccountResponseDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        return accountMapper.toDto(account);
    }
    @Transactional
    public void debit( Long accountId, BigDecimal amount,Long transactionId) {
        if (appliedTransactionRepository.existsByTransactionIdAndAccountId(transactionId, accountId)) {
            return;
        }
        Account account;
        try {
            account = accountRepository.findByIdWithLock(accountId)
                    .orElseThrow(() -> new BadRequestException("Account not found"));
        } catch (PessimisticLockingFailureException e) {
            log.warn("Locking failed for account {} during debit", accountId);
            throw new ConflictException("Account is busy, retry later", e);
        }

        account.debit(amount);
        accountRepository.save(account);
        appliedTransactionRepository.save(
                AppliedTransactions.builder()
                        .transactionId(transactionId)
                        .account(account)
                        .amount(amount)
                        .createdAt(Instant.now())
                        .build()
        );
    }

    @Transactional
    public void credit(Long accountId, BigDecimal amount, Long transactionId) {
        if (appliedTransactionRepository.existsByTransactionIdAndAccountId(transactionId, accountId)) {
            return;
        }
        Account account;
        try {
            account = accountRepository.findByIdWithLock(accountId)
                    .orElseThrow(() -> new BadRequestException("Account not found"));
        } catch (PessimisticLockingFailureException e) {
            log.warn("Locking failed for account {} during credit", accountId);
            throw new ConflictException("Account is busy, retry later", e);
        }

        account.credit(amount);
        accountRepository.save(account);
        appliedTransactionRepository.save(
                AppliedTransactions.builder()
                        .transactionId(transactionId)
                        .account(account)
                        .amount(amount)
                        .createdAt(Instant.now())
                        .build()
        );
    }
}