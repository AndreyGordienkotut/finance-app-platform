package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.service.CsvExportService;
import transaction_service.transaction_service.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions/history")
@RequiredArgsConstructor
public class TransactionHistoryController {
    private final TransactionService transactionService;
    private final CsvExportService csvExportService;


    @GetMapping
    public ResponseEntity<Page<TransactionResponseDto>> getHistory(
            @RequestParam("accountId") Long accountId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {


        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<TransactionResponseDto> history = transactionService.getHistory(accountId, pageable,user.userId());
        return ResponseEntity.ok(history);
    }
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportHistory(
            @RequestParam("accountId") Long accountId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        byte[] csv = csvExportService.exportTransactionHistory(accountId, user.userId());

        String filename = "transactions-account-" + accountId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
