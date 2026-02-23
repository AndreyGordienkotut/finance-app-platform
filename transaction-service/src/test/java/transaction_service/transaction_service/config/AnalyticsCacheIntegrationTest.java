package transaction_service.transaction_service.config;

import core.core.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import transaction_service.transaction_service.dto.TotalSpentResponse;
import transaction_service.transaction_service.model.*;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.service.AnalyticsService;
import transaction_service.transaction_service.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class AnalyticsCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);


    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    AnalyticsService analyticsService;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    TransactionService transactionService;

    private Long userId = 1L;

    @BeforeEach
    void setup() {
        transactionRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void testCacheableAndEvict() {
        BigDecimal amount = new BigDecimal("45.0000");

        Transaction tx = transactionRepository.save(
                Transaction.builder()
                        .userId(userId)
                        .sourceAccountId(1L)
                        .targetAccountId(2L)
                        .amount(amount)
                        .currency(Currency.EUR)
                        .status(Status.COMPLETED)
                        .step(TransactionStep.CREDIT_DONE)
                        .createdAt(LocalDateTime.now().minusMinutes(1))
                        .updatedAt(LocalDateTime.now())
                        .idempotencyKey("test-key-1")
                        .transactionType(TransactionType.TRANSFER)
                        .targetAmount(amount)
                        .build()
        );

        TotalSpentResponse resp1 = analyticsService.getTotalSpent(userId, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(resp1.getTotalSpent()).isEqualTo(amount);

        Set<String> keys = redisTemplate.keys("*totalSpent*");
        assertThat(keys).isNotEmpty();

        transactionService.updateStatus(tx.getId(), Status.FAILED, "Test fail");

        keys = redisTemplate.keys("total:" + userId + ":*");
        assertThat(keys).isEmpty();

        TotalSpentResponse resp2 = analyticsService.getTotalSpent(userId, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(resp2.getTotalSpent()).isEqualTo(BigDecimal.ZERO);

        keys = redisTemplate.keys("*totalSpent*");
        assertThat(keys).isNotEmpty();
    }
}
