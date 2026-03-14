package transaction_service.transaction_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.validate.AccountAccessService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvExportService {
    private final TransactionRepository transactionRepository;
    private final AccountAccessService accountAccessService;

    public byte[] exportTransactionHistory(Long accountId, Long userId) {
        accountAccessService.validateAccountOwnership(accountId, userId);

        List<Transaction> transactions = transactionRepository
                .findBySourceAccountIdOrTargetAccountId(accountId, accountId);

        log.info("Exporting {} transactions for account {}", transactions.size(), accountId);

        return buildCsv(transactions);
    }
    private byte[] buildCsv(List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();

        sb.append("id,sourceAccountId,targetAccountId,amount,targetAmount,")
                .append("exchangeRate,currency,status,transactionType,category,createdAt,updatedAt,error")
                .append("\n");

        for (Transaction tx : transactions) {
            sb.append(tx.getId()).append(",")
                    .append(nullSafe(tx.getSourceAccountId())).append(",")
                    .append(nullSafe(tx.getTargetAccountId())).append(",")
                    .append(tx.getAmount()).append(",")
                    .append(nullSafe(tx.getTargetAmount())).append(",")
                    .append(nullSafe(tx.getExchangeRate())).append(",")
                    .append(tx.getCurrency()).append(",")
                    .append(tx.getStatus()).append(",")
                    .append(tx.getTransactionType()).append(",")
                    .append(tx.getCategory() != null ? tx.getCategory().getName() : "").append(",")
                    .append(tx.getCreatedAt()).append(",")
                    .append(nullSafe(tx.getUpdatedAt())).append(",")
                    .append(tx.getErrorMessage() != null ? escapeComma(tx.getErrorMessage()) : "")
                    .append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String escapeComma(String value) {
        if (value.contains(",")) {
            return "\"" + value + "\"";
        }
        return value;
    }
}
