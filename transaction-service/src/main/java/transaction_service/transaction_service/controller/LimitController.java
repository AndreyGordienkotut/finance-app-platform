package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.LimitResponseDto;
import transaction_service.transaction_service.dto.LimitUpdateRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.TransactionLimit;
import transaction_service.transaction_service.service.LimitService;

@RestController
@RequestMapping("/api/limits")
@RequiredArgsConstructor
public class LimitController {
    private final LimitService limitService;

    @GetMapping
    public ResponseEntity<LimitResponseDto> getMyLimits(@AuthenticationPrincipal AuthenticatedUser user) {
        LimitResponseDto responseDto = limitService.getLimits(user.userId());
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping
    public ResponseEntity<LimitResponseDto> updateMyLimits(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody LimitUpdateRequestDto dto) {
        LimitResponseDto responseDto = limitService.updateLimits(user.userId(), dto.getDailyLimit(), dto.getSingleLimit());
        return ResponseEntity.ok(responseDto);
    }

}
