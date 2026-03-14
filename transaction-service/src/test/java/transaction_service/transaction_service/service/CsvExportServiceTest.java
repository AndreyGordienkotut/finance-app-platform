package transaction_service.transaction_service.service;
import core.core.enums.Currency;
import core.core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.validate.AccountAccessService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class CsvExportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountAccessService accountAccessService;

    @InjectMocks
    private CsvExportService csvExportService;

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 1L;

    private Transaction buildTx(Long id, Long sourceId, Long targetId,
                                TransactionType type, Status status,
                                TransactionCategory category, String errorMessage) {
        return Transaction.builder()
                .id(id)
                .sourceAccountId(sourceId)
                .targetAccountId(targetId)
                .amount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("90.00"))
                .exchangeRate(new BigDecimal("0.9"))
                .currency(Currency.USD)
                .status(status)
                .transactionType(type)
                .category(category)
                .createdAt(Instant.parse("2026-03-13T19:00:00Z"))
                .updatedAt(Instant.parse("2026-03-13T19:00:01Z"))
                .errorMessage(errorMessage)
                .build();
    }

    @Test
    @DisplayName("CSV contains header row")
    void export_containsHeader() {
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of());

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("id,sourceAccountId,targetAccountId,amount,targetAmount,"));
        assertTrue(content.contains("exchangeRate,currency,status,transactionType,category,createdAt,updatedAt,error"));
    }

    @Test
    @DisplayName("Empty transactions - only header row")
    void export_emptyTransactions_onlyHeader() {
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of());

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        String[] lines = content.split("\n");
        assertEquals(1, lines.length);
    }

    @Test
    @DisplayName("TRANSFER tx - all fields present in CSV row")
    void export_transferTx_allFieldsPresent() {
        TransactionCategory category = new TransactionCategory();
        category.setName("ENTERTAINMENT");

        Transaction tx = buildTx(1L, 1L, 2L, TransactionType.TRANSFER,
                Status.COMPLETED, category, null);

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("1,1,2,"));
        assertTrue(content.contains("USD"));
        assertTrue(content.contains("COMPLETED"));
        assertTrue(content.contains("TRANSFER"));
        assertTrue(content.contains("ENTERTAINMENT"));
    }

    @Test
    @DisplayName("DEPOSIT tx - sourceAccountId is empty in CSV")
    void export_depositTx_sourceAccountIdEmpty() {
        Transaction tx = buildTx(2L, null, 1L, TransactionType.DEPOSIT,
                Status.COMPLETED, null, null);

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("2,,1,"));
    }

    @Test
    @DisplayName("WITHDRAW tx - targetAccountId is empty in CSV")
    void export_withdrawTx_targetAccountIdEmpty() {
        Transaction tx = buildTx(3L, 1L, null, TransactionType.WITHDRAW,
                Status.COMPLETED, null, null);

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("3,1,,"));
    }

    @Test
    @DisplayName("Tx without category - category field is empty")
    void export_noCategory_emptyCategoryField() {
        Transaction tx = buildTx(4L, 1L, 2L, TransactionType.TRANSFER,
                Status.COMPLETED, null, null);

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("TRANSFER,,"));
    }

    @Test
    @DisplayName("Multiple transactions - correct row count")
    void export_multipleTransactions_correctRowCount() {
        List<Transaction> txs = List.of(
                buildTx(1L, 1L, 2L, TransactionType.TRANSFER, Status.COMPLETED, null, null),
                buildTx(2L, null, 1L, TransactionType.DEPOSIT, Status.COMPLETED, null, null),
                buildTx(3L, 1L, null, TransactionType.WITHDRAW, Status.COMPLETED, null, null)
        );

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(txs);

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        String[] lines = content.split("\n");
        assertEquals(4, lines.length);
    }

    @Test
    @DisplayName("Error message with comma - wrapped in quotes")
    void export_errorWithComma_escapedInCsv() {
        Transaction tx = buildTx(1L, 1L, 2L, TransactionType.TRANSFER,
                Status.FAILED, null, "Transfer failed, compensation done");

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("\"Transfer failed, compensation done\""));
    }

    @Test
    @DisplayName("Error message without comma - not wrapped in quotes")
    void export_errorWithoutComma_notEscaped() {
        Transaction tx = buildTx(1L, 1L, 2L, TransactionType.TRANSFER,
                Status.FAILED, null, "Insufficient funds");

        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        byte[] csv = csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("Insufficient funds"));
        assertFalse(content.contains("\"Insufficient funds\""));
    }

    @Test
    @DisplayName("Access denied - throws, repository not called")
    void export_accessDenied_repositoryNotCalled() {
        doThrow(new BadRequestException("Access denied"))
                .when(accountAccessService)
                .validateAccountOwnership(ACCOUNT_ID, USER_ID);

        assertThrows(BadRequestException.class,
                () -> csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID));

        verify(transactionRepository, never())
                .findBySourceAccountIdOrTargetAccountId(any(), any());
    }
    @Test
    @DisplayName("Account ownership validated before export")
    void export_ownershipValidated() {
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of());

        csvExportService.exportTransactionHistory(ACCOUNT_ID, USER_ID);

        verify(accountAccessService).validateAccountOwnership(ACCOUNT_ID, USER_ID);
    }
}