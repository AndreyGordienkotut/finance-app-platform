package transaction_service.transaction_service.service.validate;

import core.core.dto.AccountResponseDto;
import core.core.enums.StatusAccount;
import core.core.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import transaction_service.transaction_service.dto.TransactionRequestDto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith(MockitoExtension.class)
public class TransactionValidationServiceTest {

    @InjectMocks
    private TransactionValidationService validationService;

    private AccountResponseDto fromAccount;
    private AccountResponseDto toAccount;
    private TransactionRequestDto transferDto;

    @BeforeEach
    void setup() {
        fromAccount = AccountResponseDto.builder()
                .id(1L)
                .userId(100L)
                .balance(BigDecimal.valueOf(500))
                .status(StatusAccount.ACTIVE)
                .build();

        toAccount = AccountResponseDto.builder()
                .id(2L)
                .userId(100L)
                .balance(BigDecimal.valueOf(1000))
                .status(StatusAccount.ACTIVE)
                .build();

        transferDto = TransactionRequestDto.builder()
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .categoryId(10L)
                .build();
    }

    @Test
    @DisplayName("Validate: Same account -> BadRequest")
    void testValidateSameAccount() {
        transferDto.setTargetAccountId(1L);
        assertThrows(BadRequestException.class,
                () -> validationService.validateAccounts(fromAccount, toAccount, transferDto));
    }

    @Test
    @DisplayName("Validate: Account closed -> BadRequest")
    void testValidateAccountClosed() {
        fromAccount.setStatus(StatusAccount.CLOSED);
        assertThrows(BadRequestException.class,
                () -> validationService.validateAccounts(fromAccount, toAccount, transferDto));
    }

    @Test
    @DisplayName("Validate: Not enough money -> BadRequest")
    void testValidateNotEnoughMoney() {
        fromAccount.setBalance(BigDecimal.valueOf(50));
        assertThrows(BadRequestException.class,
                () -> validationService.validateAccounts(fromAccount, toAccount, transferDto));
    }

    @Test
    @DisplayName("Validate: All ok -> no exception")
    void testValidateAllOk() {
        assertDoesNotThrow(() -> validationService.validateAccounts(fromAccount, toAccount, transferDto));
    }
}