package transaction_service.transaction_service.service.validate;

import core.core.exception.FraudDetectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class FraudValidationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private FraudValidationService fraudValidationService;

    private static final Long USER_ID = 1L;

    // checkSuspiciousAmount
    @Test
    @DisplayName("Amount below threshold — passes")
    public void checkSuspiciousAmount_belowThreshold_passes() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("9999"), Instant.now().minus(30, ChronoUnit.DAYS))
        );
    }

    @Test
    @DisplayName("Amount exactly at threshold — passes")
    public void checkSuspiciousAmount_exactThreshold_passes() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("10000"), Instant.now().minus(30, ChronoUnit.DAYS))
        );
    }

    @Test
    @DisplayName("Amount above threshold — throws FraudDetectedException")
    public void checkSuspiciousAmount_aboveThreshold_throwsFraud() {
        FraudDetectedException ex = assertThrows(FraudDetectedException.class, () ->
                fraudValidationService.validate(USER_ID, new BigDecimal("10001"), Instant.now().minus(30, ChronoUnit.DAYS))
        );

        assertThat(ex.getMessage()).contains("suspiciously large");
        verify(transactionRepository, never()).countByUserIdAndCreatedAtAfter(any(), any());
    }

    //checkVelocity

    @Test
    @DisplayName("Velocity below limit — passes")
    public void checkVelocity_belowLimit_passes() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(4L);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("100"), Instant.now().minus(30, ChronoUnit.DAYS))
        );
    }

    @Test
    @DisplayName("Velocity exactly at limit — throws FraudDetectedException")
    public void checkVelocity_exactLimit_throwsFraud() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(5L);

        FraudDetectedException ex = assertThrows(FraudDetectedException.class, () ->
                fraudValidationService.validate(USER_ID, new BigDecimal("100"), Instant.now().minus(30, ChronoUnit.DAYS))
        );

        assertThat(ex.getMessage()).contains("Too many transactions");
    }

    @Test
    @DisplayName("Velocity above limit — throws FraudDetectedException")
    public void checkVelocity_aboveLimit_throwsFraud() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(10L);

        assertThrows(FraudDetectedException.class, () ->
                fraudValidationService.validate(USER_ID, new BigDecimal("100"), Instant.now().minus(30, ChronoUnit.DAYS))
        );
    }

    //checkNewAccount

    @Test
    @DisplayName("New account with large amount — throws FraudDetectedException")
    public void checkNewAccount_newAccountLargeAmount_throwsFraud() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        Instant createdYesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        FraudDetectedException ex = assertThrows(FraudDetectedException.class, () ->
                fraudValidationService.validate(USER_ID, new BigDecimal("1001"), createdYesterday)
        );

        assertThat(ex.getMessage()).contains("New account");
    }

    @Test
    @DisplayName("New account with small amount — passes")
    public void checkNewAccount_newAccountSmallAmount_passes() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        Instant createdYesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("999"), createdYesterday)
        );
    }

    @Test
    @DisplayName("Old account with large amount — passes")
    public void checkNewAccount_oldAccountLargeAmount_passes() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        Instant createdLongAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("5000"), createdLongAgo)
        );
    }

    @Test
    @DisplayName("accountCreatedAt is null — skips new account check")
    public void checkNewAccount_nullCreatedAt_skipsCheck() {
        when(transactionRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                .thenReturn(0L);

        assertDoesNotThrow(() ->
                fraudValidationService.validate(USER_ID, new BigDecimal("9999"), null)
        );
    }
}