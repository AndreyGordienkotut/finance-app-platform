package transaction_service.transaction_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.security.Principal;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponseDto> transfer(@Valid @RequestBody TransactionRequestDto dto,
                                                           Principal principal
    ,@RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = Long.parseLong(principal.getName());
        TransactionResponseDto response = transactionService.transfer(dto, userId,idempotencyKey);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponseDto> deposit (@Valid @RequestBody DepositRequestDto dto
    ,@RequestHeader("Idempotency-Key") String idempotencyKey,Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        TransactionResponseDto responseDto = transactionService.deposit(dto,idempotencyKey,userId);
        return ResponseEntity.ok(responseDto);
    }
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponseDto> withdraw(
            @Valid @RequestBody WithdrawRequestDto dto,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Principal principal) {

        Long userId = Long.parseLong(principal.getName());
        TransactionResponseDto response = transactionService.withdraw(dto, userId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TransactionResponseDto>> getHistory(
            @RequestParam("accountId") Long accountId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Principal principal) {

        Long userId = Long.parseLong(principal.getName());
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<TransactionResponseDto> history = transactionService.getHistory(accountId, pageable,userId);
        return ResponseEntity.ok(history);
    }
}
