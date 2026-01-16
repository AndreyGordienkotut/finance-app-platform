package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import transaction_service.transaction_service.dto.CategoryStatDto;
import transaction_service.transaction_service.dto.TopCategoryResponse;
import transaction_service.transaction_service.dto.TotalSpentResponse;
import transaction_service.transaction_service.service.AnalyticsService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/total")
    public ResponseEntity<TotalSpentResponse> getTotalSpent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(analyticsService.getTotalSpent(user.userId(), from, to));
    }

    @GetMapping("/top-categories")
    public ResponseEntity<List<TopCategoryResponse>> getTopCategories(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false, defaultValue = "3") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(analyticsService.getTopCategories(user.userId(), from, to, limit));
    }
    @GetMapping
    public ResponseEntity<List<CategoryStatDto>> getStats(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        return ResponseEntity.ok(analyticsService.getCategoryStats(user.userId(), fromDate, toDate));
    }


}