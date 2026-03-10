package transaction_service.transaction_service.service.validate;

import core.core.dto.AccountResponseDto;
import core.core.enums.StatusAccount;
import core.core.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.config.AccountClient;


import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountAccessServiceTest {

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private AccountAccessService accountAccessService;

    private final Long userId = 100L;

    @Test
    @DisplayName("Validate: Foreign source account -> NotFound")
    void testValidateForeignSourceAccount() {
        when(accountClient.getAccountById(1L))
                .thenReturn(AccountResponseDto.builder()
                        .id(1L)
                        .userId(999L)
                        .balance(BigDecimal.valueOf(1000))
                        .status(StatusAccount.ACTIVE)
                        .build());

        assertThrows(NotFoundException.class,
                () -> accountAccessService.validateAccountOwnership(1L, userId));
    }

    @Test
    @DisplayName("Validate: Correct account -> returns account")
    void testValidateCorrectAccount() {
        AccountResponseDto account = AccountResponseDto.builder()
                .id(1L)
                .userId(userId)
                .balance(BigDecimal.valueOf(1000))
                .status(StatusAccount.ACTIVE)
                .build();

        when(accountClient.getAccountById(1L)).thenReturn(account);

        AccountResponseDto result = accountAccessService.validateAccountOwnership(1L, userId);

        assertEquals(account, result);
    }
}
