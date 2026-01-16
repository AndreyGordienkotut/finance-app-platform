package transaction_service.transaction_service.config;

import core.core.dto.AccountResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "account-service")
public interface AccountClient {

    @GetMapping("/api/v1/accounts/{id}")
    AccountResponseDto getAccountById(@PathVariable("id") Long id);

    @PostMapping("/api/v1/accounts/{id}/debit")
    void debit(@PathVariable("id") Long id,
               @RequestParam("amount") BigDecimal amount,
               @RequestParam("txId") Long transactionId);

    @PostMapping("/api/v1/accounts/{id}/credit")
    void credit(@PathVariable("id") Long id,
                @RequestParam("amount") BigDecimal amount,
                @RequestParam("txId") Long transactionId);

}