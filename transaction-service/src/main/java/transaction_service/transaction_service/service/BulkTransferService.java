package transaction_service.transaction_service.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import transaction_service.transaction_service.config.FeignClientInterceptor;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferFailedItemDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferItemDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferRequestDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferResponseDto;
import transaction_service.transaction_service.service.validate.AccountAccessService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Service

public class BulkTransferService {
    private final TransactionService transactionService;
    private final AccountAccessService accountAccessService;


    private final Executor transactionValidationExecutor;
    public BulkTransferService(
            TransactionService transactionService,
            AccountAccessService accountAccessService,
            @Qualifier("transactionValidationExecutor") Executor transactionValidationExecutor
    ) {
        this.transactionService = transactionService;
        this.accountAccessService = accountAccessService;
        this.transactionValidationExecutor=transactionValidationExecutor;
    }
    public BulkTransferResponseDto bulkTransfer(BulkTransferRequestDto request,
                                                Long userId, String idempotencyKey){
        log.info("Starting bulk transfer for user {}, {} items", userId, request.getTransfers().size());
        accountAccessService.validateAccountOwnership(request.getSourceAccountId(), userId);
        final String authToken = extractToken();

        List<CompletableFuture<TransactionResponseDto>> futures = request.getTransfers().stream()
                .map(item -> CompletableFuture.supplyAsync(
                        () -> {
                            FeignClientInterceptor.setToken(authToken);
                            try {
                                return processItem(item, request.getSourceAccountId(), userId, idempotencyKey);
                            } finally {
                                FeignClientInterceptor.clearToken();
                            }
                        },
                        transactionValidationExecutor
                ))
                .toList();
        List<TransactionResponseDto> successful = new ArrayList<>();
        List<BulkTransferFailedItemDto> failed = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            BulkTransferItemDto item = request.getTransfers().get(i);
            try {
                TransactionResponseDto result = futures.get(i).join();
                successful.add(result);
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                log.warn("Bulk transfer item failed for target {}: {}",
                        item.getTargetAccountId(), cause.getMessage());
                failed.add(BulkTransferFailedItemDto.builder()
                        .targetAccountId(item.getTargetAccountId())
                        .amount(item.getAmount())
                        .reason(cause.getMessage())
                        .build());
            }
        }
        log.info("Bulk transfer completed for user {}: success={}, failed={}",
                userId, successful.size(), failed.size());
        return BulkTransferResponseDto.builder()
                .successful(successful)
                .failed(failed)
                .totalProcessed(request.getTransfers().size())
                .successCount(successful.size())
                .failedCount(failed.size())
                .build();
    }
    private TransactionResponseDto processItem(BulkTransferItemDto item,
                                               Long sourceAccountId,
                                               Long userId,
                                               String baseIdempotencyKey) {

        String itemKey = baseIdempotencyKey + "-" + item.getTargetAccountId();

        TransactionRequestDto dto = TransactionRequestDto.builder()
                .sourceAccountId(sourceAccountId)
                .targetAccountId(item.getTargetAccountId())
                .amount(item.getAmount())
                .categoryId(item.getCategoryId())
                .build();

        return transactionService.transfer(dto, userId, itemKey);
    }
    private String extractToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest().getHeader("Authorization");
        }
        return null;
    }
}