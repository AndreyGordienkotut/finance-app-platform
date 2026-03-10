package transaction_service.transaction_service.service;

import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.mapper.TransactionMapper;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;
import transaction_service.transaction_service.service.validate.AccountAccessService;
import transaction_service.transaction_service.service.validate.FraudValidationService;
import transaction_service.transaction_service.service.validate.TransactionValidationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final TransactionStateService transactionStateService;
    private final TransactionMapper transactionMapper;
    private Map<TransactionType, FinancialOperationStrategy> strategies;
    private final TransactionValidationService transactionValidationService;
    private final AccountAccessService accountAccessService;
    private final AccountOperationService accountOperationService;
    private final TransactionCreationService transactionCreationService;
    private final RetryBackoffService retryBackoffService;
    private final FraudValidationService fraudValidationService;

    public TransactionService(
            TransactionRepository transactionRepository,
            CategoryService categoryService,
            TransactionMapper transactionMapper,
            TransactionStateService transactionStateService,
            List<FinancialOperationStrategy> strategyList,
            TransactionValidationService transactionValidationService,
            AccountAccessService accountAccessService,
            AccountOperationService accountOperationService,
            TransactionCreationService transactionCreationService,
            RetryBackoffService retryBackoffService,
            FraudValidationService fraudValidationService

    ) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.transactionMapper = transactionMapper;
        this.transactionStateService = transactionStateService;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        FinancialOperationStrategy::getType,
                        s -> s
                ));
        this.transactionValidationService = transactionValidationService;
        this.accountAccessService = accountAccessService;
        this.accountOperationService = accountOperationService;
        this.transactionCreationService = transactionCreationService;
        this.retryBackoffService = retryBackoffService;
        this.fraudValidationService = fraudValidationService;
    }
    public TransactionResponseDto transfer(TransactionRequestDto dto, Long userId, String idempotencyKey) {

        validateIdempotency(idempotencyKey);
        AccountResponseDto from = accountAccessService.validateAccountOwnership(dto.getSourceAccountId(), userId);
        AccountResponseDto to = accountOperationService.getAccountById(dto.getTargetAccountId());
        transactionValidationService.validateAccounts(from, to, dto);


        return processTransaction(
            from.getId(),
            to.getId(),
            dto.getAmount(),
            from.getCurrency(),
            TransactionType.TRANSFER,
            idempotencyKey,
                userId,dto.getCategoryId(),
                from.getCreateAt()
        );
    }

    public TransactionResponseDto deposit(DepositRequestDto dto, String idempotencyKey, Long userId) {
        validateIdempotency(idempotencyKey);

        AccountResponseDto targetAccount = accountAccessService.validateAccountOwnership(dto.getTargetAccountId(), userId);
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
                userId,null,
                targetAccount.getCreateAt()
        );
    }

    public TransactionResponseDto withdraw(WithdrawRequestDto dto, Long userId, String idempotencyKey) {
        validateIdempotency(idempotencyKey);

        AccountResponseDto sourceAccount = accountAccessService.validateAccountOwnership(dto.getSourceAccountId(), userId);

        transactionValidationService.validateWithdraw(sourceAccount, dto);
        return processTransaction(
                sourceAccount.getId(),
                null,
                dto.getAmount(),
                sourceAccount.getCurrency(),
                TransactionType.WITHDRAW,
                idempotencyKey,
                userId,dto.getCategoryId(),
                sourceAccount.getCreateAt()

        );
    }
    public Page<TransactionResponseDto> getHistory(Long accountId, Pageable pageable,Long userId) {
        accountAccessService.validateAccountOwnership(accountId, userId);
        Page<Transaction> transactions = transactionRepository
                .findBySourceAccountIdOrTargetAccountId(accountId, accountId, pageable);

        return transactions.map(transactionMapper::toDto);
    }
    private TransactionResponseDto processTransaction(
            Long sourceAccountId, Long targetAccountId, BigDecimal amount,
            Currency currency, TransactionType type, String idempotencyKey,
            Long userId, Long categoryId, Instant accountCreatedAt)
    {
        TransactionCategory category = categoryService.validateAndGetCategory(categoryId, userId, type);
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            TransactionResponseDto txDto = transactionMapper.toDto(tx);
            return txDto;
        }
        fraudValidationService.validate(userId, amount, accountCreatedAt);
        Transaction tx;
        try {
            tx = transactionCreationService.createTransaction(sourceAccountId, targetAccountId, amount, currency, type, idempotencyKey,userId,category);
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
                transactionStateService.updateStatus(tx.getId(), Status.PROCESSING, "Attempt " + attempt);

                Transaction currentTx = transactionRepository.findById(tx.getId())
                        .orElseThrow(() -> new NotFoundException("Transaction not found"));

                strategies.get(type)
                        .execute(currentTx, sourceAccountId, targetAccountId, tx.getTargetAmount());
                transactionStateService.updateStatus(currentTx.getId(), Status.COMPLETED, null);
                return transactionMapper.toDto(transactionRepository.findById(currentTx.getId()).orElseThrow());

            } catch (ConflictException e) {
                if (!(e.getCause() instanceof PessimisticLockingFailureException)) {
                    throw e;
                }
                log.warn("Retry Attempt {} failed for TX {}: Account busy (Lock).", attempt, tx.getId());

                if (attempt == maxAttempts) {
                    log.error("Max retry attempts reached for TX {}. Marking as FAILED.", tx.getId());
                    transactionStateService.updateStatus(tx.getId(), Status.FAILED, "Max retry attempts reached: " + e.getMessage());
                    throw e;
                }
                try {
                    retryBackoffService.backoff(attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new InternalServerErrorException("Retry interrupted");
                }
            } catch (BadRequestException e) {
                transactionStateService.updateStatus(tx.getId(), Status.FAILED, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("TX {} unexpected error: {}", tx.getId(), e.getMessage(), e);
                transactionStateService.updateStatus(tx.getId(), Status.FAILED, "Unexpected error: " + e.getMessage());
                throw new InternalServerErrorException("Unexpected error during transaction processing");
            }
        }

        throw new InternalServerErrorException("Transaction failed after all retries");

    }

    private void validateIdempotency(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required.");
        }
    }
}
