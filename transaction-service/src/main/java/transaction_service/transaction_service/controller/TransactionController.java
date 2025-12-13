package transaction_service.transaction_service.controller;

import core.core.AccountResponseDto;
import core.core.ErrorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.exception.*;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.service.TransactionService;

import java.security.Principal;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponseDto> transfer(@RequestBody TransactionRequestDto dto,
                                                           Principal principal
    ,@RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = Long.parseLong(principal.getName());
        TransactionResponseDto response = transactionService.transfer(dto, userId,idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
