package transaction_service.transaction_service.service;

import core.core.AccountResponseDto;
import core.core.Currency;
import core.core.StatusAccount;
import feign.FeignException;
import feign.Logger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.exception.*;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.RollBackStatus;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public TransactionResponseDto transfer(TransactionRequestDto dto, Long userId) {
        AccountResponseDto from = accountClient.getAccountById(dto.getFromAccountId());
        AccountResponseDto to = accountClient.getAccountById(dto.getToAccountId());
        log.info("TX START: from={}, to={}, amount={}, userId={}",
                dto.getFromAccountId(), dto.getToAccountId(), dto.getAmount(), userId);
        Transaction tx = createPending(dto, from.getCurrency());
        try {
            log.info("TX {} validating accounts", tx.getId());
            validateAccounts(from, to, dto, userId);
            boolean debitSucceeded = false;
            try {
                log.info("TX {} calling debit for account {}", tx.getId(), from.getId());
                accountClient.debit(from.getId(), dto.getAmount(),tx.getId());
                log.info("TX {} debit succeeded: from={}, amount={}", tx.getId(), from.getId(), dto.getAmount());
                debitSucceeded = true;
                accountClient.credit(to.getId(), dto.getAmount(),tx.getId());
                log.info("TX {} credit succeeded: to={}, amount={}", tx.getId(), to.getId(), dto.getAmount());
                updateStatus(tx.getId(), Status.SUCCESS, null);
                Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
                log.info("TX {} finished SUCCESS", tx.getId());
                return convertToDto(updated);


            } catch (FeignException e) {
                log.warn("TX {} remote call failed: {}", tx.getId(), e.getMessage());

                if (debitSucceeded) {
                    log.warn("TX {} credit failed, starting rollback", tx.getId());

                    compensate(tx.getId(), from.getId(), dto.getAmount());

                    Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
                    return convertToDto(updated);
                } else {
                    updateStatus(tx.getId(), Status.FAILED, "Debit failed: " + e.getMessage());
                    throw new BadRequestException("Debit failed: " + e.getMessage());
                }
            }

        } catch (BadRequestException e) {
            log.warn("TX {} validation failed with status {}: {}", tx.getId(),Status.FAILED, e.getMessage());
            updateStatus(tx.getId(), Status.FAILED, e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("TX {} unexpected error with status {} : {}", tx.getId(),Status.ERROR, e.getMessage());
            updateStatus(tx.getId(), Status.ERROR, e.getMessage());
            throw new InternalServerErrorException("Unexpected error");
        }
    }
    @Transactional
    public Transaction createPending(TransactionRequestDto dto, Currency getCurrency) {
        Transaction tx = Transaction.builder()
                .fromAccountId(dto.getFromAccountId())
                .toAccountId(dto.getToAccountId())
                .amount(dto.getAmount())
                .status(Status.PENDING)
                .currency(getCurrency)
                .createdAt(LocalDateTime.now())
                .rollbackStatus(RollBackStatus.NONE)
                .build();
        log.info("TX {} created SUCCESS", tx.getId());
        return transactionRepository.save(tx);
    }
    private void validateAccounts(AccountResponseDto from, AccountResponseDto to,
                                  TransactionRequestDto dto, Long userId) {
        log.debug("TX balance check: balance={}, amount={}", from.getBalance(), dto.getAmount());
        if (!from.getUserId().equals(userId)) {
            throw new BadRequestException("This account does not belong to you");
        }
        if (dto.getFromAccountId().equals(dto.getToAccountId())) {
            throw new BadRequestException("Same account");
        }

        if (from.getStatus() == StatusAccount.CLOSED || to.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Account is closed");
        }

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BadRequestException("Different currencies");
        }

        if (from.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money");
        }


    }
    @Transactional
    public void updateStatus(Long id, Status status, String error) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        log.info("TX {} updated status: {}", tx.getId(), status);
        tx.setStatus(status);
        tx.setErrorMessage(error);
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);


    }
    @Transactional
    public boolean compensate(Long txId, Long fromAccountId, BigDecimal amount) {
        try {
            log.info("TX {} rollback: returning money to account {}", txId, fromAccountId);
            accountClient.credit(fromAccountId, amount,txId);
            updateRollback(txId, Status.FAILED, RollBackStatus.SUCCESS, null);
            return true;
        } catch (FeignException e) {
            log.error("TX {} rollback FAILED: {}", txId, e.getMessage());
            updateRollback(txId, Status.FAILED, RollBackStatus.FAILED, "Rollback failed: " + e.getMessage());
            return false;
        }
    }
    @Transactional
    public void updateRollback(Long txId, Status status, RollBackStatus rbStatus, String error) {
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        tx.setStatus(status);
        tx.setRollbackStatus(rbStatus);
        tx.setErrorMessage(error);
        tx.setUpdatedAt(LocalDateTime.now());
        log.info("TX {} status changed: {} (error={})", txId, status, error);
        transactionRepository.save(tx);
    }
    private TransactionResponseDto convertToDto(Transaction transaction) {
        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getFromAccountId(),
                transaction.getToAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getErrorMessage(),
                transaction.getUpdatedAt(),
                transaction.getRollbackStatus()
        );
    }

}
