package transaction_service.transaction_service.controller;

import core.core.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import transaction_service.transaction_service.dto.CategoryRequestDto;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<TransactionCategory>> getCategories(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.getAllCategoriesForUser(user.userId()));
    }

    @PostMapping
    public ResponseEntity<TransactionCategory> createCategory(@RequestBody CategoryRequestDto dto, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCustomCategory(dto.getName(), user.userId()));
    }

}