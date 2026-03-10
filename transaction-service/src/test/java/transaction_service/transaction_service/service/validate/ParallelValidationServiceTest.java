package transaction_service.transaction_service.service.validate;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.exception.FraudDetectedException;
import core.core.exception.InternalServerErrorException;
import core.core.exception.LimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.dto.ValidationResult;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.AccountOperationService;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.LimitService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParallelValidationServiceTest {
    @Mock
    private LimitService limitService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private FraudValidationService fraudValidationService;
    @Mock
    private AccountOperationService accountOperationService;

    private ParallelValidationService parallelValidationService;

    private static final Long USER_ID = 1L;
    private static final Long TARGET_ACCOUNT_ID = 2L;
    private static final Instant OLD_ACCOUNT = Instant.now().minus(30, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        parallelValidationService = new ParallelValidationService(
                limitService,
                exchangeRateService,
                fraudValidationService,
                accountOperationService,
                Runnable::run
        );
    }

    //resolveRate

    @Test
    @DisplayName("Same currency TRANSFER - rate is ONE")
    void validate_sameCurrency_rateIsOne() {
        AccountResponseDto target = AccountResponseDto.builder()
                .currency(Currency.USD)
                .build();
        when(accountOperationService.getAccountById(TARGET_ACCOUNT_ID)).thenReturn(target);

        ValidationResult result = parallelValidationService.validate(
                USER_ID, BigDecimal.valueOf(100), Currency.USD,
                TARGET_ACCOUNT_ID, TransactionType.TRANSFER, OLD_ACCOUNT
        );

        assertEquals(BigDecimal.ONE, result.getRate());
        assertEquals(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP), result.getTargetAmount());
        verify(exchangeRateService, never()).getRate(any(), any());
    }

    @Test
    @DisplayName("Different currency TRANSFER - rate fetched and applied")
    void validate_differentCurrency_rateApplied() {
        AccountResponseDto target = AccountResponseDto.builder()
                .currency(Currency.EUR)
                .build();
        when(accountOperationService.getAccountById(TARGET_ACCOUNT_ID)).thenReturn(target);
        when(exchangeRateService.getRate(Currency.USD, Currency.EUR))
                .thenReturn(new BigDecimal("0.9"));

        ValidationResult result = parallelValidationService.validate(
                USER_ID, BigDecimal.valueOf(100), Currency.USD,
                TARGET_ACCOUNT_ID, TransactionType.TRANSFER, OLD_ACCOUNT
        );

        assertEquals(new BigDecimal("0.9"), result.getRate());
        assertEquals(new BigDecimal("90.00"), result.getTargetAmount());
    }

    @Test
    @DisplayName("DEPOSIT - rate is ONE, no account fetch")
    void validate_deposit_rateIsOne() {
        ValidationResult result = parallelValidationService.validate(
                USER_ID, BigDecimal.valueOf(500), Currency.USD,
                null, TransactionType.DEPOSIT, OLD_ACCOUNT
        );

        assertEquals(BigDecimal.ONE, result.getRate());
        verify(accountOperationService, never()).getAccountById(any());
    }

    //parallel validation

    @Test
    @DisplayName("All checks pass - returns ValidationResult")
    void validate_allPass_returnsResult() {
        ValidationResult result = parallelValidationService.validate(
                USER_ID, BigDecimal.valueOf(100), Currency.USD,
                null, TransactionType.WITHDRAW, OLD_ACCOUNT
        );

        assertNotNull(result);
        verify(limitService).checkTransactionLimit(eq(USER_ID), any());
        verify(fraudValidationService).validate(eq(USER_ID), any(), any());
    }

    @Test
    @DisplayName("Limit exceeded - throws LimitExceededException")
    void validate_limitExceeded_throws() {
        doThrow(new LimitExceededException("Daily limit exceeded"))
                .when(limitService).checkTransactionLimit(any(), any());

        assertThrows(LimitExceededException.class, () ->
                parallelValidationService.validate(
                        USER_ID, BigDecimal.valueOf(100), Currency.USD,
                        null, TransactionType.WITHDRAW, OLD_ACCOUNT
                )
        );
        verify(fraudValidationService).validate(any(), any(), any());
    }

    @Test
    @DisplayName("Fraud detected - throws FraudDetectedException")
    void validate_fraudDetected_throws() {
        doThrow(new FraudDetectedException("Suspicious amount"))
                .when(fraudValidationService).validate(any(), any(), any());

        assertThrows(FraudDetectedException.class, () ->
                parallelValidationService.validate(
                        USER_ID, BigDecimal.valueOf(100), Currency.USD,
                        null, TransactionType.WITHDRAW, OLD_ACCOUNT
                )
        );
    }

    @Test
    @DisplayName("Unknown exception wrapped in InternalServerErrorException")
    void validate_unknownException_wrapsToInternal() {
        doThrow(new RuntimeException("Unexpected"))
                .when(limitService).checkTransactionLimit(any(), any());

        assertThrows(InternalServerErrorException.class, () ->
                parallelValidationService.validate(
                        USER_ID, BigDecimal.valueOf(100), Currency.USD,
                        null, TransactionType.WITHDRAW, OLD_ACCOUNT
                )
        );
    }
}
