package account_service.account_service.controller;

import account_service.account_service.dto.AccountRequestDto;
import core.core.AccountResponseDto;
import account_service.account_service.model.Account;
import account_service.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    @PostMapping("/create")
    public ResponseEntity<AccountResponseDto> createAccount(@RequestBody AccountRequestDto requestDto, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        AccountResponseDto accountResponseDto = accountService.createAccount(requestDto, userId);
        return new ResponseEntity<>(accountResponseDto, HttpStatus.CREATED);
    }
    @GetMapping
    public ResponseEntity<Page<AccountResponseDto>> getAccounts(Principal principal, Pageable pageable) {
        Long userId = Long.parseLong(principal.getName());
        Page<AccountResponseDto> accountResponseDto = accountService.getAllAccounts(userId,pageable);
        return new ResponseEntity<>(accountResponseDto, HttpStatus.OK);

    }
    @PostMapping("/{accountId}/close")
    public ResponseEntity<AccountResponseDto> closeAccount(@PathVariable Long accountId, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        AccountResponseDto accountResponseDto = accountService.closeAccount(accountId,userId);
        return new ResponseEntity<>(accountResponseDto, HttpStatus.OK);
    }

    //accountClient
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDto> getAccountById(@PathVariable Long id) {
        AccountResponseDto account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<Void> debit(@PathVariable Long id, @RequestParam BigDecimal amount, @RequestParam Long transactionId) {
        accountService.debit(id, amount,transactionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<Void> credit(@PathVariable Long id, @RequestParam BigDecimal amount ,@RequestParam Long transactionId) {
        accountService.credit(id, amount,transactionId);
        return ResponseEntity.ok().build();
    }
}
