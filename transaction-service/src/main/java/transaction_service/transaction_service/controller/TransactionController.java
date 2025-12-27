package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import core.core.enums.Currency;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponseDto> transfer(@Valid @RequestBody TransactionRequestDto dto,
                                                           @AuthenticationPrincipal AuthenticatedUser user,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        TransactionResponseDto response = transactionService.transfer(dto, user.userId(),idempotencyKey);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponseDto> deposit (@Valid @RequestBody DepositRequestDto dto
    ,@RequestHeader("Idempotency-Key") String idempotencyKey,@AuthenticationPrincipal AuthenticatedUser user) {

        TransactionResponseDto responseDto = transactionService.deposit(dto,idempotencyKey,user.userId());
        return ResponseEntity.ok(responseDto);
    }
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponseDto> withdraw(
            @Valid @RequestBody WithdrawRequestDto dto,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user) {

        TransactionResponseDto response = transactionService.withdraw(dto, user.userId(), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
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
    @GetMapping("/exchange-preview")
    public ResponseEntity<BigDecimal> previewExchange(
            @RequestParam Currency from,
            @RequestParam Currency to,
            @RequestParam BigDecimal amount) {
        BigDecimal rate = exchangeRateService.getRate(from, to);
        return ResponseEntity.ok(exchangeRateService.convert(amount, rate));
    }
}
