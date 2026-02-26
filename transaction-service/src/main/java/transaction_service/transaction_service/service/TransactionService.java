package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.*;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.config.AccountClient;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.event.TransactionCompletedEvent;
import transaction_service.transaction_service.mapper.TransactionMapper;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final LimitService limitService;
    private final AccountClient accountClient;
    private final ExchangeRateService exchangeRateService;
    private final CategoryService categoryService;

    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private Map<TransactionType, FinancialOperationStrategy> strategies;

    public TransactionService(
            TransactionRepository transactionRepository,
            LimitService limitService,
            AccountClient accountClient,
            ExchangeRateService exchangeRateService,
            CategoryService categoryService,
            TransactionMapper transactionMapper,
            ApplicationEventPublisher eventPublisher,
            List<FinancialOperationStrategy> strategyList
    ) {
        this.transactionRepository = transactionRepository;
        this.limitService = limitService;
        this.accountClient = accountClient;
        this.exchangeRateService = exchangeRateService;
        this.categoryService = categoryService;
        this.transactionMapper = transactionMapper;
        this.eventPublisher = eventPublisher;

        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        FinancialOperationStrategy::getType,
                        s -> s
                ));
    }
    public TransactionResponseDto transfer(TransactionRequestDto dto, Long userId, String idempotencyKey) {

        validateIdempotency(idempotencyKey);
        AccountResponseDto from = validateAccountOwnership(dto.getSourceAccountId(), userId);
        AccountResponseDto to = accountClient.getAccountById(dto.getTargetAccountId());
        validateAccounts(from, to, dto, userId);


        return processTransaction(
            from.getId(),
            to.getId(),
            dto.getAmount(),
            from.getCurrency(),
            TransactionType.TRANSFER,
            idempotencyKey,
                userId,dto.getCategoryId()
        );
    }

    public TransactionResponseDto deposit(DepositRequestDto dto, String idempotencyKey, Long userId) {
        validateIdempotency(idempotencyKey);

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
                TransactionType.DEPOSIT,
                idempotencyKey,
                userId,null
        );
    }

    public TransactionResponseDto withdraw(WithdrawRequestDto dto, Long userId, String idempotencyKey) {
        validateIdempotency(idempotencyKey);

        AccountResponseDto sourceAccount = validateAccountOwnership(dto.getSourceAccountId(), userId);

        validateWithdraw(sourceAccount, dto, userId);
        return processTransaction(
                sourceAccount.getId(),
                null,
                dto.getAmount(),
                sourceAccount.getCurrency(),
                TransactionType.WITHDRAW,
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

        return transactions.map(transactionMapper::toDto);
    }
    private TransactionResponseDto processTransaction(
            Long sourceAccountId, Long targetAccountId, BigDecimal amount,
            Currency currency, TransactionType type, String idempotencyKey,
            Long userId, Long categoryId)
    {
        TransactionCategory category = categoryService.validateAndGetCategory(categoryId, userId, type);
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            TransactionResponseDto txDto = transactionMapper.toDto(tx);
            return txDto;
        }
        Transaction tx;
        try {
            tx = createTransaction(sourceAccountId, targetAccountId, amount, currency, type, idempotencyKey,userId,category);
        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency Key Conflict: Another process saved the transaction first. Key: {}", idempotencyKey);
            Transaction conflictTx = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new InternalServerErrorException("Internal conflict handling error."));
            return transactionMapper.toDto(conflictTx);
        }
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("Attempt {}/{} for TX {}: starting financial operations", attempt, maxAttempts, tx.getId());
                updateStatus(tx.getId(), Status.PROCESSING, "Attempt " + attempt);

                Transaction currentTx = transactionRepository.findById(tx.getId())
                        .orElseThrow(() -> new NotFoundException("Transaction not found"));

                strategies.get(type)
                        .execute(currentTx, sourceAccountId, targetAccountId, tx.getTargetAmount());
                updateStatus(currentTx.getId(), Status.COMPLETED, null);
                return transactionMapper.toDto(transactionRepository.findById(currentTx.getId()).orElseThrow());

            } catch (ConflictException e) {
                if (!(e.getCause() instanceof PessimisticLockingFailureException)) {
                    throw e;
                }

                log.warn("Retry Attempt {} failed for TX {}: Account busy (Lock).", attempt, tx.getId());

                if (attempt == maxAttempts) {
                    log.error("Max retry attempts reached for TX {}. Marking as FAILED.", tx.getId());
                    updateStatus(tx.getId(), Status.FAILED, "Max retry attempts reached: " + e.getMessage());
                    throw e;
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new InternalServerErrorException("Retry interrupted");
                }
            } catch (BadRequestException e) {
                updateStatus(tx.getId(), Status.FAILED, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("TX {} unexpected error: {}", tx.getId(), e.getMessage(), e);
                updateStatus(tx.getId(), Status.FAILED, "Unexpected error: " + e.getMessage());
                throw new InternalServerErrorException("Unexpected error during transaction processing");
            }
        }

        throw new InternalServerErrorException("Transaction failed after all retries");

    }

    @Transactional
    public Transaction createTransaction(Long sourceId, Long targetId, BigDecimal amount,
                                         Currency currency, TransactionType type, String idempotencyKey, Long userId, TransactionCategory category) {


        BigDecimal rate = BigDecimal.ONE;
        BigDecimal targetAmount = amount;
        if (type == TransactionType.TRANSFER && targetId != null) {
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
                .transactionType(type)
                .step(TransactionStep.NONE)
                .category(category)
                .build();

        BigDecimal limitAmount = type == TransactionType.TRANSFER ? targetAmount : amount;
        limitService.checkTransactionLimit(userId, limitAmount);
        Transaction saved = transactionRepository.save(tx);
        log.info("TX {} created (Type: {})", saved.getId(), type);
        return saved;
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
    public void executeDebit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling debit for account {}", txId, accountId);
        accountClient.debit(accountId, amount, txId);
    }

    public void executeCredit(Long txId, Long accountId, BigDecimal amount) {
        log.info("TX {} calling credit for account {}", txId, accountId);
        accountClient.credit(accountId, amount, txId);
    }
    private void validateAccounts(AccountResponseDto from, AccountResponseDto to,
                                  TransactionRequestDto dto, Long userId) {
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
    private void validateWithdraw(AccountResponseDto source, WithdrawRequestDto dto, Long userId) {
        log.debug("TX balance check for withdraw: balance={}, amount={}", source.getBalance(), dto.getAmount());
        if (source.getStatus() == StatusAccount.CLOSED) {
            throw new BadRequestException("Account is closed.");
        }
        if (source.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException("Not enough money in source account for withdrawal.");
        }
    }
    private void validateIdempotency(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required.");
        }
    }
    private AccountResponseDto validateAccountOwnership(Long accountId, Long userId) {
        AccountResponseDto account = accountClient.getAccountById(accountId);
        if (!account.getUserId().equals(userId)) {
            throw new NotFoundException("Account not found or access denied.");
        }
        return account;
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
        if (status == Status.COMPLETED || status == Status.FAILED) {
            eventPublisher.publishEvent(new TransactionCompletedEvent(tx.getUserId()));
        }

    }

    public void compensate(Long txId, Long fromAccountId, BigDecimal amount) {
        try {
            log.info("TX {} compensation: returning money to account {}", txId, fromAccountId);
            accountClient.credit(fromAccountId, amount, txId);
            log.info("TX {} compensation SUCCESS", txId);
        } catch (FeignException e) {
            log.error("TX {} compensation FAILED: {}", txId, e.getMessage());
            throw new RuntimeException("Compensation failed", e);
        }
    }

}
