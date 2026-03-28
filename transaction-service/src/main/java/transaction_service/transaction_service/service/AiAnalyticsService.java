package transaction_service.transaction_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import transaction_service.transaction_service.config.ClaudeClient;
import transaction_service.transaction_service.dto.AiSummaryResponse;
import transaction_service.transaction_service.dto.CategoryStatDto;
import transaction_service.transaction_service.dto.TopCategoryResponse;
import transaction_service.transaction_service.dto.TotalSpentResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalyticsService {

    private final AnalyticsService analyticsService;
    private final ClaudeClient claudeClient;

    public AiSummaryResponse getSummary(Long userId) {
        log.info("Building AI summary for user {}", userId);

        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant to = Instant.now();

        TotalSpentResponse totalSpent = analyticsService.getTotalSpent(userId, from, to);
        List<TopCategoryResponse> topCategories = analyticsService.getTopCategories(userId, from, to, 5);
        List<CategoryStatDto> categoryStats = analyticsService.getCategoryStats(userId, from, to);

        String prompt = buildPrompt(totalSpent, topCategories, categoryStats);
        String summary = claudeClient.analyze(prompt);

        return AiSummaryResponse.builder()
                .summary(summary)
                .analyzedFrom(from)
                .analyzedTo(to)
                .totalSpent(totalSpent.getTotalSpent())
                .build();
    }

    private String buildPrompt(TotalSpentResponse totalSpent,
                               List<TopCategoryResponse> topCategories,
                               List<CategoryStatDto> categoryStats) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a personal finance advisor. Analyze this user's spending data and provide actionable advice in 3-4 sentences.\n\n");
        sb.append("Total spent last 30 days: ").append(totalSpent.getTotalSpent()).append("\n\n");

        sb.append("Top categories:\n");
        topCategories.forEach(cat ->
                sb.append("- ").append(cat.getName())
                        .append(": ").append(cat.getTotal()).append("\n")
        );

        sb.append("\nAll category stats:\n");
        categoryStats.forEach(stat ->
                sb.append("- ").append(stat.getName())
                        .append(": ").append(stat.getTotalAmount()).append("\n")
        );

        sb.append("\nProvide advice in the same language the categories are written in. Be specific and helpful.");

        return sb.toString();
    }
}