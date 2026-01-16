package transaction_service.transaction_service.service;

import core.core.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.repository.TransactionCategoryRepository;
import transaction_service.transaction_service.repository.TransactionRepository;


import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final TransactionCategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public List<TransactionCategory> getAllCategoriesForUser(Long userId) {
        return categoryRepository.findByUserIdOrUserIdIsNull(userId);
    }

    public TransactionCategory createCustomCategory(String name, Long userId) {
        categoryRepository.findByNameAndUserId(name, userId)
                .ifPresent(c -> { throw new BadRequestException("Category already exists"); });
        log.info("Creating custom category '{}' for user {}", name, userId);
        return categoryRepository.save(TransactionCategory.builder()
                .name(name)
                .userId(userId)
                .build());
    }


    public TransactionCategory validateAndGetCategory(Long categoryId, Long userId, TransactionType type) {
        if (type == TransactionType.DEPOSIT) return null;

        if (categoryId == null) {
            throw new BadRequestException("Category is required for transaction type: " + type);
        }

        TransactionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found with id: " + categoryId));

        if (category.getUserId() != null && !Objects.equals(category.getUserId(), userId)) {
            log.warn("User {} tried to use private category {} of user {}", userId, categoryId, category.getUserId());
            throw new ForbiddenException("You cannot use this category");
        }

        return category;
    }
}