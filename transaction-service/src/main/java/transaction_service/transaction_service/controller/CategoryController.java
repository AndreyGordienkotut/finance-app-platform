package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.CategoryRequestDto;
import transaction_service.transaction_service.dto.CategoryStatDto;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.service.CategoryService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping("/categories")
    public ResponseEntity<List<TransactionCategory>> getCategories(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.getAllCategoriesForUser(user.userId()));
    }

    @PostMapping("/categories")
    public ResponseEntity<TransactionCategory> createCategory(@RequestBody CategoryRequestDto dto, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCustomCategory(dto.getName(), user.userId()));
    }

    @GetMapping("/transaction/stats")
    public ResponseEntity<List<CategoryStatDto>> getStats(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        return ResponseEntity.ok(categoryService.getCategoryStats(user.userId(), fromDate, toDate));
    }
}