package transaction_service.transaction_service.service;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.BadRequestException;
import core.core.exception.FraudDetectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferItemDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferRequestDto;
import transaction_service.transaction_service.dto.bulk.BulkTransferResponseDto;
import transaction_service.transaction_service.service.validate.AccountAccessService;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class BulkTransferServiceTest {
    @Mock
    private TransactionService transactionService;
    @Mock
    private AccountAccessService accountAccessService;

    private BulkTransferService bulkTransferService;

    private static final Long USER_ID = 1L;
    private static final Long SOURCE_ACCOUNT_ID = 1L;
    private static final Long TARGET_ACCOUNT_ID_1 = 2L;
    private static final Long TARGET_ACCOUNT_ID_2 = 3L;
    private static final String IDEMPOTENCY_KEY = "bulk-key-123";

    private BulkTransferRequestDto request;
    private AccountResponseDto sourceAccount;
    @BeforeEach
    void setUp() {

        bulkTransferService = new BulkTransferService(
                transactionService,
                accountAccessService,
                Executors.newSingleThreadExecutor()
        );

        sourceAccount = AccountResponseDto.builder()
                .id(SOURCE_ACCOUNT_ID)
                .userId(USER_ID)
                .currency(Currency.USD)
                .status(StatusAccount.ACTIVE)
                .build();

        request = BulkTransferRequestDto.builder()
                .sourceAccountId(SOURCE_ACCOUNT_ID)
                .transfers(List.of(
                        BulkTransferItemDto.builder()
                                .targetAccountId(TARGET_ACCOUNT_ID_1)
                                .amount(new BigDecimal("100.00"))
                                .build(),
                        BulkTransferItemDto.builder()
                                .targetAccountId(TARGET_ACCOUNT_ID_2)
                                .amount(new BigDecimal("200.00"))
                                .build()
                ))
                .build();

        lenient().when(accountAccessService.validateAccountOwnership(SOURCE_ACCOUNT_ID, USER_ID))
                .thenReturn(sourceAccount);
    }


    @Test
    @DisplayName("All transfers succeed - successCount equals total")
    void bulkTransfer_allSucceed_correctCounts() {
        when(transactionService.transfer(any(), eq(USER_ID), any()))
                .thenReturn(new TransactionResponseDto());

        BulkTransferResponseDto result =
                bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        assertNotNull(result);
        assertEquals(2, result.getTotalProcessed());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getFailed().isEmpty());
    }

    @Test
    @DisplayName("All transfers succeed - transfer() called for each item")
    void bulkTransfer_allSucceed_transferCalledForEachItem() {
        when(transactionService.transfer(any(), eq(USER_ID), any()))
                .thenReturn(new TransactionResponseDto());

        bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        verify(transactionService, times(2)).transfer(any(), eq(USER_ID), any());
    }

    @Test
    @DisplayName("Idempotency key is unique per target account")
    void bulkTransfer_idempotencyKey_uniquePerItem() {
        when(transactionService.transfer(any(), eq(USER_ID), any()))
                .thenReturn(new TransactionResponseDto());

        bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        verify(transactionService).transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_1));
        verify(transactionService).transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_2));
    }

    @Test
    @DisplayName("One transfer fails - goes to failed list with reason")
    void bulkTransfer_oneFails_inFailedList() {
        when(transactionService.transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_1)))
                .thenReturn(new TransactionResponseDto());

        when(transactionService.transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_2)))
                .thenThrow(new BadRequestException("Insufficient funds"));

        BulkTransferResponseDto result =
                bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        assertEquals(2, result.getTotalProcessed());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals("Insufficient funds", result.getFailed().get(0).getReason());
        assertEquals(TARGET_ACCOUNT_ID_2, result.getFailed().get(0).getTargetAccountId());
    }

    @Test
    @DisplayName("All transfers fail - all in failed list")
    void bulkTransfer_allFail_allInFailedList() {
        when(transactionService.transfer(any(), eq(USER_ID), any()))
                .thenThrow(new BadRequestException("Insufficient funds"));

        BulkTransferResponseDto result =
                bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        assertEquals(2, result.getTotalProcessed());
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFailedCount());
        assertTrue(result.getSuccessful().isEmpty());
    }

    @Test
    @DisplayName("Fraud detected on one item - goes to failed, others proceed")
    void bulkTransfer_fraudOnOneItem_othersSucceed() {
        when(transactionService.transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_1)))
                .thenReturn(new TransactionResponseDto());

        when(transactionService.transfer(any(), eq(USER_ID),
                eq(IDEMPOTENCY_KEY + "-" + TARGET_ACCOUNT_ID_2)))
                .thenThrow(new FraudDetectedException("Transaction amount is suspiciously large"));

        BulkTransferResponseDto result =
                bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertThat(result.getFailed().get(0).getReason()).contains("suspiciously large");
    }

    @Test
    @DisplayName("Source account validation called once")
    void bulkTransfer_sourceAccountValidated() {
        when(transactionService.transfer(any(), eq(USER_ID), any()))
                .thenReturn(new TransactionResponseDto());

        bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY);

        verify(accountAccessService, times(1))
                .validateAccountOwnership(SOURCE_ACCOUNT_ID, USER_ID);
    }

    @Test
    @DisplayName("Source account access denied - throws, no transfers executed")
    void bulkTransfer_accessDenied_noTransfersExecuted() {
        doThrow(new BadRequestException("Access denied"))
                .when(accountAccessService)
                .validateAccountOwnership(SOURCE_ACCOUNT_ID, USER_ID);

        assertThrows(BadRequestException.class,
                () -> bulkTransferService.bulkTransfer(request, USER_ID, IDEMPOTENCY_KEY));

        verify(transactionService, never()).transfer(any(), any(), any());
    }
}

