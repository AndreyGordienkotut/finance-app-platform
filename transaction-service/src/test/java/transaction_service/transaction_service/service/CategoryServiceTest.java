package transaction_service.transaction_service.service;

import core.core.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.repository.TransactionCategoryRepository;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {
    @Mock
    private TransactionCategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Long userId;
    private String categoryName;
    private TransactionCategory globalCategory;
    private TransactionCategory userCategory;

    @BeforeEach
    void setUp() {
        userId = 1L;
        categoryName = "Coffee";

        globalCategory = TransactionCategory.builder()
                .id(100L)
                .name("FOOD")
                .userId(null)
                .build();

        userCategory = TransactionCategory.builder()
                .id(200L)
                .name("Hobby")
                .userId(userId)
                .build();
    }
    //getAllCategoriesForUser
    @Test
    @DisplayName("Should return both global and user categories")
    void getAllCategoriesForUser_ReturnsCombined() {
        when(categoryRepository.findByUserIdOrUserIdIsNull(userId))
                .thenReturn(List.of(globalCategory, userCategory));

        List<TransactionCategory> result = categoryService.getAllCategoriesForUser(userId);
        assertEquals(2, result.size());
        verify(categoryRepository).findByUserIdOrUserIdIsNull(userId);
    }
    //createCustomCategory
    @Test
    @DisplayName("Should create custom category successfully")
    void createCustomCategory_Success() {
        when(categoryRepository.findByNameAndUserId(categoryName, userId)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(TransactionCategory.class))).thenReturn(userCategory);

        TransactionCategory result = categoryService.createCustomCategory(categoryName, userId);

        assertNotNull(result);
        verify(categoryRepository).save(any(TransactionCategory.class));
    }

    @Test
    @DisplayName("Should throw BadRequest when category name already exists for user")
    void createCustomCategory_AlreadyExists() {
        when(categoryRepository.findByNameAndUserId(categoryName, userId)).thenReturn(Optional.of(userCategory));

        assertThrows(BadRequestException.class, () ->
                categoryService.createCustomCategory(categoryName, userId));

        verify(categoryRepository, never()).save(any());
    }


    //validateAndGetCategory

    @Test
    @DisplayName("Should return null for DEPOSIT type")
    void validate_ReturnNullForDeposit() {
        TransactionCategory result = categoryService.validateAndGetCategory(1L, userId, TransactionType.DEPOSIT);
        assertNull(result);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("Should throw BadRequest when categoryId is null for non-deposit")
    void validate_ThrowsIfIdIsNull() {
        assertThrows(BadRequestException.class, () ->
                categoryService.validateAndGetCategory(null, userId, TransactionType.TRANSFER));
    }

    @Test
    @DisplayName("Should throw BadRequest if category not found")
    void validate_ThrowsIfNotFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                categoryService.validateAndGetCategory(999L, userId, TransactionType.TRANSFER));
    }

    @Test
    @DisplayName("Should throw Forbidden when using another user's private category")
    void validate_ThrowsIfForbidden() {
        TransactionCategory otherUserCategory = TransactionCategory.builder()
                .id(300L)
                .userId(999L)
                .build();
        when(categoryRepository.findById(300L)).thenReturn(Optional.of(otherUserCategory));

        assertThrows(ForbiddenException.class, () ->
                categoryService.validateAndGetCategory(300L, userId, TransactionType.TRANSFER));
    }

    @Test
    @DisplayName("Should allow global category (userId is null)")
    void validate_AllowsGlobalCategory() {
        when(categoryRepository.findById(100L)).thenReturn(Optional.of(globalCategory));

        TransactionCategory result = categoryService.validateAndGetCategory(100L, userId, TransactionType.TRANSFER);

        assertNotNull(result);
        assertNull(result.getUserId());
    }

    @Test
    @DisplayName("Should allow own private category")
    void validate_AllowsOwnCategory() {
        when(categoryRepository.findById(200L)).thenReturn(Optional.of(userCategory));

        TransactionCategory result = categoryService.validateAndGetCategory(200L, userId, TransactionType.TRANSFER);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
    }
}
