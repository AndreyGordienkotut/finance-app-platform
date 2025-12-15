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

    @GetMapping("/api/account/{id}")
    AccountResponseDto getAccountById(@PathVariable("id") Long id);

    @PostMapping("/api/account/{id}/debit")
    void debit(@PathVariable("id") Long id,
               @RequestParam("amount") BigDecimal amount,
               @RequestParam("txId") Long transactionId);

    @PostMapping("/api/account/{id}/credit")
    void credit(@PathVariable("id") Long id,
                @RequestParam("amount") BigDecimal amount,
                @RequestParam("txId") Long transactionId);

}