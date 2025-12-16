package account_service.account_service.service;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.model.Account;
import account_service.account_service.model.AppliedTransactions;
import account_service.account_service.repository.AccountRepository;
import account_service.account_service.repository.AppliedTransactionRepository;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AppliedTransactionRepository appliedTransactionRepository;
    @InjectMocks
    private AccountService accountService;


    private final Long USER_ID = 1L;
    private final Long OTHER_USER_ID = 99L;
    private final Long ACCOUNT_ID = 10L;
    private final Long TX_ID = 100L;

    private Account activeAccount;
    private Account closedAccount;

    @BeforeEach
    void setUp() {
        activeAccount = Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .currency(Currency.USD)
                .balance(BigDecimal.valueOf(500))
                .statusAccount(StatusAccount.ACTIVE)
                .createAt(LocalDateTime.now())
                .build();

        closedAccount = Account.builder()
                .id(11L)
                .userId(USER_ID)
                .currency(Currency.EUR)
                .balance(BigDecimal.ZERO)
                .statusAccount(StatusAccount.CLOSED)
                .createAt(LocalDateTime.now())
                .build();
    }
    @Test
    @DisplayName("Succeed account creation")
    void testCreateAccount_Success() {
        AccountRequestDto request = new AccountRequestDto(Currency.USD);
        when(accountRepository.countByUserIdAndStatusAccount(USER_ID, StatusAccount.ACTIVE)).thenReturn(5L);

        accountService.createAccount(request, USER_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());

        Account savedAccount = accountCaptor.getValue();
        assertEquals(USER_ID, savedAccount.getUserId());
        assertEquals(Currency.USD, savedAccount.getCurrency());
        assertEquals(BigDecimal.ZERO, savedAccount.getBalance());
        assertEquals(StatusAccount.ACTIVE, savedAccount.getStatusAccount());
    }
}

