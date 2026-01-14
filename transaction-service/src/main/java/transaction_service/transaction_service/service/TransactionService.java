package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.*;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final LimitService limitService;
    private final AccountClient accountClient;
    private final ExchangeRateService exchangeRateService;
    private final CategoryService categoryService;

    @Transactional
    @CacheEvict(value = {"totalSpent", "topCategories", "timeline"}, allEntries = true)
    public TransactionResponseDto transfer(TransactionRequestDto dto, Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
           throw new BadRequestException("Idempotency-Key header is required.");
        }

        AccountResponseDto from = validateAccountOwnership(dto.getSourceAccountId(), userId);
        AccountResponseDto to = accountClient.getAccountById(dto.getTargetAccountId());
        validateAccounts(from, to, dto, userId);

        return processTransaction(
            from.getId(),
            to.getId(),
            dto.getAmount(),
            from.getCurrency(),
            TypeTransaction.TRANSFER,
            idempotencyKey,
                userId,dto.getCategoryId()
        );
    }
    @Transactional
    @CacheEvict(value = {"totalSpent", "topCategories", "timeline"}, allEntries = true)
    public TransactionResponseDto deposit(DepositRequestDto dto, String idempotencyKey, Long userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required.");
        }
        AccountResponseDto targetAccount = validateAccountOwnership(dto.getTargetAccountId(), userId);
        if (!targetAccount.getUserId().equals(userId)) {
            throw new BadRequestException("Account does not belong to you");
        }
        if (targetAccount.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Target account is closed.");
        }

        return processTransaction(
                null,
                targetAccount.getId(),
                dto.getAmount(),
                targetAccount.getCurrency(),
                TypeTransaction.DEPOSIT,
                idempotencyKey,
                userId,null
        );
    }
    @Transactional
    @CacheEvict(value = {"totalSpent", "topCategories", "timeline"}, allEntries = true)
    public TransactionResponseDto withdraw(WithdrawRequestDto dto, Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required.");
        }

        AccountResponseDto sourceAccount = validateAccountOwnership(dto.getSourceAccountId(), userId);

        validateWithdraw(sourceAccount, dto, userId);

        return processTransaction(
                sourceAccount.getId(),
                null,
                dto.getAmount(),
                sourceAccount.getCurrency(),
                TypeTransaction.WITHDRAW,
                idempotencyKey,
                userId,dto.getCategoryId()

        );
    }
    public Page<TransactionResponseDto> getHistory(Long accountId, Pageable pageable,Long userId) {
        validateAccountOwnership(accountId, userId);
        AccountResponseDto account = accountClient.getAccountById(accountId);
        if (!account.getUserId().equals(userId)) {
            throw new NotFoundException("Account not found or access denied for this user.");
        }
        Page<Transaction> transactions = transactionRepository
                .findBySourceAccountIdOrTargetAccountId(accountId, accountId, pageable);

        return transactions.map(this::convertToDto);
    }
    private TransactionResponseDto processTransaction(
            Long sourceAccountId, Long targetAccountId, BigDecimal amount,
            Currency currency, TypeTransaction type, String idempotencyKey,
            Long userId,Long categoryId)
    {
        TransactionCategory category = categoryService.validateAndGetCategory(categoryId, userId, type);
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            TransactionResponseDto txDto = convertToDto(tx);
            if (tx.getStatus() == Status.CREATED || tx.getStatus() == Status.PROCESSING) {
                throw new ConflictException("Transaction is already pending with this key and is being processed.", txDto);
            }
            return txDto;
        }
        Transaction tx;
        try {
            tx = createTransaction(sourceAccountId, targetAccountId, amount, currency, type, idempotencyKey,userId,category);
            updateStatus(tx.getId(), Status.PROCESSING, null);
        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency Key Conflict: Another process saved the transaction first. Key: {}", idempotencyKey);
            Transaction conflictTx = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new InternalServerErrorException("Internal conflict handling error."));

            TransactionResponseDto conflictTxDto = convertToDto(conflictTx);
            if (conflictTx.getStatus() == Status.CREATED) {
                throw new ConflictException("Transaction with this key is currently being processed.", conflictTxDto);
            }

            return conflictTxDto;
        }
        try {
            executeFinancialOperations(tx, type, sourceAccountId, targetAccountId, amount);

            updateStatus(tx.getId(), Status.COMPLETED, null);
            return convertToDto(transactionRepository.findById(tx.getId()).orElseThrow());

        } catch (BadRequestException e) {
            updateStatus(tx.getId(), Status.FAILED, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("TX {} unexpected error: {}", tx.getId(), e.getMessage(), e);
            updateStatus(tx.getId(), Status.FAILED, "Unexpected error: " + e.getMessage());
            throw new InternalServerErrorException("Unexpected error");
        }
    }
     void executeFinancialOperations(Transaction tx, TypeTransaction type,
                                            Long sourceAccountId, Long targetAccountId, BigDecimal amount) {
        if (type == TypeTransaction.TRANSFER) {
            executeSaga(tx, sourceAccountId, targetAccountId, amount);
        } else if (type == TypeTransaction.DEPOSIT) {
            executeCredit(tx.getId(), targetAccountId, amount);
        } else if (type == TypeTransaction.WITHDRAW) {
            executeDebit(tx.getId(), sourceAccountId, amount);
        }
    }

    @Transactional
    public Transaction createTransaction(Long sourceId, Long targetId, BigDecimal amount,
                                         Currency currency, TypeTransaction type, String idempotencyKey, Long userId, TransactionCategory category) {


        BigDecimal rate = BigDecimal.ONE;
        BigDecimal targetAmount = amount;
        if (type == TypeTransaction.TRANSFER && targetId != null) {
            AccountResponseDto targetAcc = accountClient.getAccountById(targetId);
            if (!currency.equals(targetAcc.getCurrency())) {
                rate = exchangeRateService.getRate(currency, targetAcc.getCurrency());
                targetAmount = exchangeRateService.convert(amount, rate);
            }
        }
        Transaction tx = Transaction.builder()
                .userId(userId)
                .sourceAccountId(sourceId)
                .targetAccountId(targetId)
                .amount(amount)
                .targetAmount(targetAmount)
                .exchangeRate(rate)
                .status(Status.CREATED)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .typeTransaction(type)
                .step(TransactionStep.NONE)
                .category(category)
                .build();

        BigDecimal limitAmount = type == TypeTransaction.TRANSFER ? targetAmount : amount;
        limitService.checkTransactionLimit(userId, limitAmount);
        Transaction saved = transactionRepository.save(tx);
        log.info("TX {} created (Type: {})", saved.getId(), type);
        return saved;
    }
    private void executeSaga(Transaction tx, Long fromId, Long toId, BigDecimal amount) {
        boolean debitSucceeded = tx.getStep() == TransactionStep.DEBIT_DONE;
        try {
            if (tx.getStep() == TransactionStep.NONE) {
                log.info("TX {} SAGA: Debit {} from account {}", tx.getId(), amount, fromId);
                executeDebit(tx.getId(), fromId, amount);
                updateStep(tx.getId(), TransactionStep.DEBIT_DONE);
                debitSucceeded = true;
            }

            if (tx.getStep() == TransactionStep.DEBIT_DONE) {
                log.info("TX {} SAGA: Credit {} to account {}", tx.getId(), tx.getTargetAmount(), toId);
                executeCredit(tx.getId(), toId, tx.getTargetAmount());
                updateStep(tx.getId(), TransactionStep.CREDIT_DONE);
            }
        } catch (FeignException e) {
            log.warn("TX {} remote call failed: {}", tx.getId(), e.getMessage());

            if (debitSucceeded) {
                log.warn("TX {} Credit failed, starting compensation (rollback)", tx.getId());
                try {
                    compensate(tx.getId(), fromId, amount);
                } catch (RuntimeException re) {
                    log.error("TX {} compensation FAILED: {}", tx.getId(), re.getMessage());
                    updateStatus(tx.getId(), Status.FAILED, "Transfer failed. Compensation failed: " + re.getMessage());
                    throw new BadRequestException("Transfer failed. Compensation failed.");
                }
            }
            throw new BadRequestException("Transfer failed: " + e.getMessage());
        }
    }
    @Transactional
    public void updateStep(Long txId, TransactionStep step) {
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        tx.setStep(step);
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        log.info("TX {} step updated to {}", txId, step);
    }
    private void executeDebit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling debit for account {}", txId, accountId);
        accountClient.debit(accountId, amount, txId);
    }

    private void executeCredit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling credit for account {}", txId, accountId);
        accountClient.credit(accountId, amount, txId);
    }
    private void validateAccounts(AccountResponseDto from, AccountResponseDto to,
                                  TransactionRequestDto dto, Long userId) {
        log.debug("TX balance check: balance={}, amount={}", from.getBalance(), dto.getAmount());

        if (dto.getSourceAccountId().equals(dto.getTargetAccountId())) {
            throw new BadRequestException("Same account");
        }

        if (from.getStatus() == StatusAccount.CLOSED || to.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Account is closed");
        }

        if (from.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money");
        }
    }
    private void validateWithdraw(AccountResponseDto source, WithdrawRequestDto dto, Long userId) {
        log.debug("TX balance check for withdraw: balance={}, amount={}", source.getBalance(), dto.getAmount());
        if (source.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Account is closed.");
        }
        if (source.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money in source account for withdrawal.");
        }
    }
    private AccountResponseDto validateAccountOwnership(Long accountId, Long userId) {
        AccountResponseDto account = accountClient.getAccountById(accountId);
        if (!account.getUserId().equals(userId)) {
            throw new NotFoundException("Account not found or access denied.");
        }
        return account;
    }
    @CacheEvict(value = {"totalSpent", "topCategories", "timeline"}, allEntries = true)
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

    public void compensate(Long txId, Long fromAccountId, BigDecimal amount) {
        try {
            log.info("TX {} compensation: returning money to account {}", txId, fromAccountId);
            accountClient.credit(fromAccountId, amount, txId);
            log.info("TX {} compensation SUCCESS", txId);
        } catch (FeignException e) {
            log.error("TX {} compensation FAILED: {}", txId, e.getMessage());
//            updateStatus(txId, Status.FAILED, "Compensation failed: " + e.getMessage());
            throw new RuntimeException("Compensation failed", e);
        }
    }

    private TransactionResponseDto convertToDto(Transaction transaction) {
        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                transaction.getTargetAmount(),
                transaction.getExchangeRate(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getErrorMessage(),
                transaction.getUpdatedAt(),
                transaction.getTypeTransaction(),
                transaction.getCategory()
        );
    }

}
