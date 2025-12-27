package transaction_service.transaction_service.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import transaction_service.transaction_service.dto.ExchangeRateResponseDto;

@FeignClient(name = "exchange-rate-client", url = "https://api.frankfurter.app")
public interface ExchangeRateClient {

    @GetMapping("/latest")
    ExchangeRateResponseDto getLatestRate(
            @RequestParam("from") String from,
            @RequestParam("to") String to
    );
}