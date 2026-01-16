package account_service.account_service.controller;

import account_service.account_service.dto.AccountRequestDto;
import core.core.dto.AccountResponseDto;
import account_service.account_service.service.AccountService;
import core.core.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    @PostMapping
    public ResponseEntity<AccountResponseDto> createAccount(@Valid @RequestBody AccountRequestDto requestDto,
                                                            @AuthenticationPrincipal AuthenticatedUser user) {
        AccountResponseDto accountResponseDto = accountService.createAccount(requestDto, user.userId());
        return new ResponseEntity<>(accountResponseDto, HttpStatus.CREATED);
    }
    @GetMapping
    public ResponseEntity<Page<AccountResponseDto>> getAccounts( @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {

        Page<AccountResponseDto> accountResponseDto = accountService.getAllAccounts(user.userId(),pageable);
        return new ResponseEntity<>(accountResponseDto, HttpStatus.OK);

    }
    @PostMapping("/{accountId}/close")
    public ResponseEntity<AccountResponseDto> closeAccount(@PathVariable Long accountId,  @AuthenticationPrincipal AuthenticatedUser user) {
        AccountResponseDto accountResponseDto = accountService.closeAccount(accountId,user.userId());
        return new ResponseEntity<>(accountResponseDto, HttpStatus.OK);
    }

    //accountClient
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDto> getAccountById(@PathVariable Long id) {

        AccountResponseDto account = accountService.getAccountById(id);

        return ResponseEntity.ok(account);
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<Void> debit(@PathVariable Long id,
                                      @RequestParam("amount") BigDecimal amount,
                                      @RequestParam("txId") Long transactionId
                                       ) {
        accountService.debit(id, amount,transactionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<Void> credit(@PathVariable Long id,
                                       @RequestParam("amount") BigDecimal amount,
                                       @RequestParam("txId") Long transactionId  ) {
        accountService.credit(id, amount,transactionId);
        return ResponseEntity.ok().build();
    }
}
