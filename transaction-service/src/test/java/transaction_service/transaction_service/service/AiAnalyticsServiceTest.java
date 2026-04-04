package transaction_service.transaction_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.config.ClaudeClient;
import transaction_service.transaction_service.dto.AiSummaryResponse;
import transaction_service.transaction_service.dto.CategoryStatDto;
import transaction_service.transaction_service.dto.TopCategoryResponse;
import transaction_service.transaction_service.dto.TotalSpentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiAnalyticsServiceTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ClaudeClient claudeClient;

    private AiAnalyticsService aiAnalyticsService;

    private Long userId;

    @BeforeEach
    void setUp() {
        aiAnalyticsService = new AiAnalyticsService(analyticsService, claudeClient);
        userId = 1L;
    }

    @Test
    @DisplayName("getSummary: builds response from analytics and AI text")
    void getSummary_happyPath() {
        TotalSpentResponse totalSpent = TotalSpentResponse.builder()
                .totalSpent(new BigDecimal("250.50"))
                .currency("MIXED_CURRENCY")
                .build();
        List<TopCategoryResponse> topCategories = List.of(
                TopCategoryResponse.builder()
                        .categoryId(1L)
                        .name("FOOD")
                        .total(new BigDecimal("100.00"))
                        .build(),
                TopCategoryResponse.builder()
                        .categoryId(2L)
                        .name("RENT")
                        .total(new BigDecimal("150.50"))
                        .build()
        );
        List<CategoryStatDto> categoryStats = List.of(
                CategoryStatDto.builder()
                        .id(1L)
                        .name("FOOD")
                        .totalAmount(new BigDecimal("100.00"))
                        .build()
        );
        String aiText = "You spent most on rent; consider reviewing subscriptions.";

        when(analyticsService.getTotalSpent(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(totalSpent);
        when(analyticsService.getTopCategories(eq(userId), any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(topCategories);
        when(analyticsService.getCategoryStats(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(categoryStats);
        when(claudeClient.analyze(anyString())).thenReturn(aiText);

        AiSummaryResponse result = aiAnalyticsService.getSummary(userId);

        assertNotNull(result);
        assertEquals(aiText, result.getSummary());
        assertEquals(new BigDecimal("250.50"), result.getTotalSpent());
        assertNotNull(result.getAnalyzedFrom());
        assertNotNull(result.getAnalyzedTo());
        assertTrue(result.getAnalyzedTo().isAfter(result.getAnalyzedFrom()));
        assertEquals(30, ChronoUnit.DAYS.between(result.getAnalyzedFrom(), result.getAnalyzedTo()));

        verify(analyticsService).getTotalSpent(eq(userId), any(Instant.class), any(Instant.class));
        verify(analyticsService).getTopCategories(eq(userId), any(Instant.class), any(Instant.class), eq(5));
        verify(analyticsService).getCategoryStats(eq(userId), any(Instant.class), any(Instant.class));
        verify(claudeClient).analyze(anyString());
    }

    @Test
    @DisplayName("getSummary: empty categories and zero total still returns AI summary")
    void getSummary_emptyCategories() {
        TotalSpentResponse totalSpent = TotalSpentResponse.builder()
                .totalSpent(BigDecimal.ZERO)
                .currency("MIXED_CURRENCY")
                .build();
        String aiText = "No spending in the last 30 days.";

        when(analyticsService.getTotalSpent(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(totalSpent);
        when(analyticsService.getTopCategories(eq(userId), any(Instant.class), any(Instant.class), eq(5)))
                .thenReturn(Collections.emptyList());
        when(analyticsService.getCategoryStats(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(claudeClient.analyze(anyString())).thenReturn(aiText);

        AiSummaryResponse result = aiAnalyticsService.getSummary(userId);

        assertNotNull(result);
        assertEquals(aiText, result.getSummary());
        assertEquals(BigDecimal.ZERO, result.getTotalSpent());

        verify(claudeClient).analyze(anyString());
    }
}
