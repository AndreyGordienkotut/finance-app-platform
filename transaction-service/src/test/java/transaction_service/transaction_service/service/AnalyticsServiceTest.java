package transaction_service.transaction_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import transaction_service.transaction_service.dto.TimelineResponse;
import transaction_service.transaction_service.dto.TopCategoryResponse;
import transaction_service.transaction_service.dto.TotalSpentResponse;
import transaction_service.transaction_service.repository.TransactionRepository;

import core.core.exception.*;
import transaction_service.transaction_service.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {
    @Mock
    private  TransactionRepository transactionRepository;
    @InjectMocks
    private AnalyticsService analyticsService;
    private Long userId;
    private LocalDateTime from;
    private LocalDateTime to;
    @BeforeEach
    void setUp() {
        userId = 1L;
        from = LocalDateTime.now().minusDays(7);
        to = LocalDateTime.now();
    }

    //Tests for getTotalSpent

    @Test
    @DisplayName("Should return total spent when transactions exist")
    void getTotalSpent_ReturnsValue() {
        BigDecimal expectedTotal = new BigDecimal("150.00");
        when(transactionRepository.getTotalSpent(eq(userId), eq(Status.COMPLETED), any(), any()))
                .thenReturn(expectedTotal);

        TotalSpentResponse result = analyticsService.getTotalSpent(userId, from, to);

        assertEquals(expectedTotal, result.getTotalSpent());
        assertEquals("MIXED_CURRENCY", result.getCurrency());
        verify(transactionRepository)
                .getTotalSpent(eq(userId), eq(Status.COMPLETED), any(), any());
    }

    @Test
    @DisplayName("Should return zero when repository returns null")
    void getTotalSpent_ReturnsZero_WhenRepoReturnsNull() {
        when(transactionRepository.getTotalSpent(anyLong(), any(), any(), any()))
                .thenReturn(null);

        TotalSpentResponse result = analyticsService.getTotalSpent(userId, from, to);

        assertEquals(BigDecimal.ZERO, result.getTotalSpent());
    }

    @Test
    @DisplayName("Should use default dates when from/to are null")
    void getTotalSpent_UsesDefaultDates() {
        analyticsService.getTotalSpent(userId, null, null);

        verify(transactionRepository).getTotalSpent(eq(userId), eq(Status.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when from > to")
    void getTotalSpent_ThrowsException_WhenFromAfterTo() {
        LocalDateTime invalidFrom = LocalDateTime.now();
        LocalDateTime invalidTo = LocalDateTime.now().minusDays(1);

        assertThrows(BadRequestException.class, () ->
                analyticsService.getTotalSpent(userId, invalidFrom, invalidTo));

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should throw BadRequestException when range > 365 days")
    void getTotalSpent_ThrowsException_WhenRangeTooLarge() {
        LocalDateTime longAgo = LocalDateTime.now().minusDays(400);

        assertThrows(BadRequestException.class, () ->
                analyticsService.getTotalSpent(userId, longAgo, LocalDateTime.now()));
    }
    //Tests for getTopCategories

    @Test
    @DisplayName("Should correctly map results to TopCategoryResponse")
    void getTopCategories_MapsCorrectly() {
        List<Object[]> mockData = List.of(
                new Object[]{1L, "FOOD", new BigDecimal("100.00")},
                new Object[]{2L, "RENT", new BigDecimal("500.00")}
        );
        when(transactionRepository.getTopCategories(eq(userId), eq(Status.COMPLETED), any(), any(), any(Pageable.class)))
                .thenReturn(mockData);

        List<TopCategoryResponse> result = analyticsService.getTopCategories(userId, from, to, 2);

        assertEquals(2, result.size());
        assertEquals("FOOD", result.get(0).getName());
        assertEquals(new BigDecimal("500.00"), result.get(1).getTotal());
    }

    @Test
    @DisplayName("Should pass correct limit via Pageable")
    void getTopCategories_UsesCorrectLimit() {
        int limit = 5;
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        analyticsService.getTopCategories(userId, from, to, limit);

        verify(transactionRepository).getTopCategories(any(), any(), any(), any(), pageableCaptor.capture());
        assertEquals(limit, pageableCaptor.getValue().getPageSize());
    }

    //Tests for getTimeline

    @Test
    @DisplayName("Should correctly convert SQL Date to LocalDate in timeline")
    void getTimeline_ConvertsDatesCorrectly() {
        java.sql.Date sqlDate = java.sql.Date.valueOf("2025-01-01");

        List<Object[]> mockData = Collections.singletonList(new Object[]{sqlDate, new BigDecimal("42.00")});

        when(transactionRepository.getTimelineData(eq(userId), any(), any(), eq("day")))
                .thenReturn(mockData);

        List<TimelineResponse> result = analyticsService.getTimeline(userId, from, to, "day");

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2025, 1, 1), result.get(0).getPeriod());
        assertEquals(new BigDecimal("42.00"), result.get(0).getTotal());
    }

    @Test
    @DisplayName("Should handle empty timeline data")
    void getTimeline_HandlesEmptyData() {
        when(transactionRepository.getTimelineData(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<TimelineResponse> result = analyticsService.getTimeline(userId, from, to, "month");

        assertTrue(result.isEmpty());
    }
}
