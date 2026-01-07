package transaction_service.transaction_service.service;

import core.core.exception.LimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.model.TransactionLimit;
import transaction_service.transaction_service.repository.TransactionLimitRepository;
import transaction_service.transaction_service.repository.TransactionRepository;
import transaction_service.transaction_service.dto.*;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

    @Mock
    private TransactionLimitRepository transactionLimitRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private LimitService limitService;

    private Long userId;
    private TransactionLimit userLimit;
    private BigDecimal defaultDaily = new BigDecimal("5000");
    private BigDecimal defaultSingle = new BigDecimal("1000");

    @BeforeEach
    void setUp() {
        userId = 1L;
        userLimit = new TransactionLimit();
        userLimit.setUserId(userId);
        userLimit.setDailyLimit(defaultDaily);
        userLimit.setSingleLimit(defaultSingle);
    }

    //checkTransactionLimit

    @Test
    @DisplayName("Should create default limit if none exists during check")
    void checkLimit_CreatesDefaultIfMissing() {
        BigDecimal amount = new BigDecimal("100");
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(transactionLimitRepository.save(any(TransactionLimit.class))).thenReturn(userLimit);
        when(transactionRepository.calculateTotalSpentForUserInLast24Hours(eq(userId), any())).thenReturn(BigDecimal.ZERO);

        assertDoesNotThrow(() -> limitService.checkTransactionLimit(userId, amount));

        verify(transactionLimitRepository).save(any(TransactionLimit.class));
    }

    @Test
    @DisplayName("Should throw LimitExceededException when single transaction exceeds limit")
    void checkLimit_ThrowsOnSingleLimitExceeded() {
        BigDecimal expensiveAmount = new BigDecimal("1001");
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));

        assertThrows(LimitExceededException.class, () ->
                limitService.checkTransactionLimit(userId, expensiveAmount));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should throw LimitExceededException when daily limit is exceeded")
    void checkLimit_ThrowsOnDailyLimitExceeded() {
        BigDecimal amount = new BigDecimal("600");
        BigDecimal alreadySpent = new BigDecimal("4500"); // 4500 + 600 > 5000

        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));
        when(transactionRepository.calculateTotalSpentForUserInLast24Hours(eq(userId), any()))
                .thenReturn(alreadySpent);

        assertThrows(LimitExceededException.class, () ->
                limitService.checkTransactionLimit(userId, amount));
    }

    @Test
    @DisplayName("Should handle null spentInLast24h as zero")
    void checkLimit_HandlesNullSpentAsZero() {
        BigDecimal amount = new BigDecimal("100");
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));

        when(transactionRepository.calculateTotalSpentForUserInLast24Hours(eq(userId), any()))
                .thenReturn(null);

        assertDoesNotThrow(() -> limitService.checkTransactionLimit(userId, amount));
    }

    @Test
    @DisplayName("Should succeed if within limits")
    void checkLimit_Success() {
        BigDecimal amount = new BigDecimal("500");
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));
        when(transactionRepository.calculateTotalSpentForUserInLast24Hours(eq(userId), any()))
                .thenReturn(new BigDecimal("1000"));

        assertDoesNotThrow(() -> limitService.checkTransactionLimit(userId, amount));
    }

    //getLimits

    @Test
    @DisplayName("Should return existing limits")
    void getLimits_ReturnsExisting() {
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));

        LimitResponseDto result = limitService.getLimits(userId);

        assertEquals(defaultDaily, result.getDailyLimit());
        assertEquals(defaultSingle, result.getSingleLimit());
    }

    @Test
    @DisplayName("Should create default if getting limits for new user")
    void getLimits_CreatesDefaultIfMissing() {
        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(transactionLimitRepository.save(any(TransactionLimit.class))).thenReturn(userLimit);

        limitService.getLimits(userId);

        verify(transactionLimitRepository).save(any(TransactionLimit.class));
    }

    //updateLimits

    @Test
    @DisplayName("Should update values and save")
    void updateLimits_UpdatesAndSaves() {
        BigDecimal newDaily = new BigDecimal("10000");
        BigDecimal newSingle = new BigDecimal("2000");

        when(transactionLimitRepository.findByUserId(userId)).thenReturn(Optional.of(userLimit));
        when(transactionLimitRepository.save(any(TransactionLimit.class))).thenAnswer(i -> i.getArguments()[0]);

        LimitResponseDto result = limitService.updateLimits(userId, newDaily, newSingle);

        assertEquals(newDaily, result.getDailyLimit());
        assertEquals(newSingle, result.getSingleLimit());
        verify(transactionLimitRepository).save(userLimit);
    }
}
