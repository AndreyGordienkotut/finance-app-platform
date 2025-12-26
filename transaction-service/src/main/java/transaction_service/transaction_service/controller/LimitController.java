package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.LimitUpdateRequestDto;
import transaction_service.transaction_service.model.TransactionLimit;
import transaction_service.transaction_service.service.LimitService;

@RestController
@RequestMapping("/api/limits")
@RequiredArgsConstructor
public class LimitController {
    private final LimitService limitService;

    @GetMapping
    public ResponseEntity<TransactionLimit> getMyLimits(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(limitService.getLimits(user.userId()));
    }

    @PutMapping
    public ResponseEntity<TransactionLimit> updateMyLimits(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody LimitUpdateRequestDto dto) {

        return ResponseEntity.ok(limitService.updateLimits(user.userId(), dto.getDailyLimit(), dto.getSingleLimit()));
    }
}
