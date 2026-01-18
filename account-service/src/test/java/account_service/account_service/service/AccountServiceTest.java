package account_service.account_service.service;

import account_service.account_service.model.AppliedTransactions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.model.Account;
import account_service.account_service.repository.AccountRepository;
import account_service.account_service.repository.AppliedTransactionRepository;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.*;


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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    //create Account
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
    @Test
    @DisplayName("Fail account creation due to limit")
    void testCreateAccount_LimitExceeded() {
        AccountRequestDto request = new AccountRequestDto(Currency.USD);
        when(accountRepository.countByUserIdAndStatusAccount(USER_ID, StatusAccount.ACTIVE)).thenReturn(10L); // Max is 10

        assertThrows(BadRequestException.class,
                () -> accountService.createAccount(request, USER_ID),
                "Expected BadRequestException for limit exceeded");

        verify(accountRepository, never()).save(any());
    }
    //getAllAccounts
    @Test
    @DisplayName("Succeed get all accounts")
    void testGetAllAccounts_Success() {
        Pageable pageable= PageRequest.of(0, 10);
        List<Account> accountList = List.of(activeAccount, closedAccount);
        Page<Account> accountPage = new PageImpl<>(accountList, pageable, accountList.size());

        when(accountRepository.findAllByUserIdAndStatusAccountNotOrderByCreateAtAsc(USER_ID, StatusAccount.CLOSED, pageable))
                .thenReturn(accountPage);

        Page<AccountResponseDto> result = accountService.getAllAccounts(USER_ID, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(ACCOUNT_ID, result.getContent().get(0).getId());
    }
    //closeAccount
    @Test
    @DisplayName("Succeed closing account (Zero balance)")
    void testCloseAccount_Success() {
        activeAccount.setBalance(BigDecimal.ZERO);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        AccountResponseDto result = accountService.closeAccount(ACCOUNT_ID, USER_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());

        assertEquals(StatusAccount.CLOSED, accountCaptor.getValue().getStatusAccount());
        assertEquals(StatusAccount.CLOSED, result.getStatus());
    }

    @Test
    @DisplayName("Fail closing account due to non-zero balance")
    void testCloseAccount_NonZeroBalance() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount)); // Balance 500

        assertThrows(BadRequestException.class,
                () -> accountService.closeAccount(ACCOUNT_ID, USER_ID),
                "Expected BadRequestException for non-zero balance");

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fail closing account due to ownership (Wrong User ID)")
    void testCloseAccount_WrongOwner() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        assertThrows(BadRequestException.class,
                () -> accountService.closeAccount(ACCOUNT_ID, OTHER_USER_ID),
                "Expected BadRequestException for wrong ownership");

        verify(accountRepository, never()).save(any());
    }
    //getAccountById
    @Test
    @DisplayName("Succeed get account by ID")
    void testGetAccountById_Success() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        AccountResponseDto result = accountService.getAccountById(ACCOUNT_ID);

        assertEquals(ACCOUNT_ID, result.getId());
        assertEquals(BigDecimal.valueOf(500), result.getBalance());
        verify(accountRepository).findById(ACCOUNT_ID);
    }

    @Test
    @DisplayName("Fail get account by ID (Not Found)")
    void testGetAccountById_NotFound() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> accountService.getAccountById(ACCOUNT_ID),
                "Expected NotFoundException for Account Not Found (as per service implementation)");
    }
    //debit
    @Test
    @DisplayName("Succeed debit transaction")
    void testDebit_Success() {
        BigDecimal debitAmount = BigDecimal.valueOf(100);
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        accountService.debit(ACCOUNT_ID, debitAmount, TX_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());

        assertEquals(BigDecimal.valueOf(400), accountCaptor.getValue().getBalance());

        verify(appliedTransactionRepository).save(any(AppliedTransactions.class));
    }

    @Test
    @DisplayName("Debit is idempotent (Already applied)")
    void testDebit_Idempotent() {
        BigDecimal debitAmount = BigDecimal.valueOf(100);
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, ACCOUNT_ID)).thenReturn(true);

        accountService.debit(ACCOUNT_ID, debitAmount, TX_ID);

        verify(accountRepository, never()).findById(anyLong());
        verify(accountRepository, never()).save(any());
        verify(appliedTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fail debit due to insufficient funds")
    void testDebit_InsufficientFunds() {
        BigDecimal debitAmount = BigDecimal.valueOf(600);
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        assertThrows(BadRequestException.class,
                () -> accountService.debit(ACCOUNT_ID, debitAmount, TX_ID),
                "Expected BadRequestException for Insufficient Funds");

        verify(accountRepository, never()).save(any());
        verify(appliedTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fail debit due to inactive account")
    void testDebit_InactiveAccount() {
        BigDecimal debitAmount = BigDecimal.valueOf(100);
        closedAccount.setBalance(BigDecimal.valueOf(1000));
        closedAccount.setStatusAccount(StatusAccount.CLOSED);

        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, closedAccount.getId())).thenReturn(false);
        when(accountRepository.findByIdWithLock(closedAccount.getId())).thenReturn(Optional.of(closedAccount));

        assertThrows(BadRequestException.class,
                () -> accountService.debit(closedAccount.getId(), debitAmount, TX_ID),
                "Expected BadRequestException for Inactive Account");

        verify(accountRepository, never()).save(any());
    }
    //credit
    @Test
    @DisplayName("Succeed credit transaction")
    void testCredit_Success() {
        BigDecimal creditAmount = BigDecimal.valueOf(150);
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

        accountService.credit(ACCOUNT_ID, creditAmount, TX_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());

        assertEquals(BigDecimal.valueOf(650), accountCaptor.getValue().getBalance());

        verify(appliedTransactionRepository).save(any(AppliedTransactions.class));
    }

    @Test
    @DisplayName("Credit is idempotent (Already applied)")
    void testCredit_Idempotent() {
        BigDecimal creditAmount = BigDecimal.valueOf(150);
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, ACCOUNT_ID)).thenReturn(true);

        accountService.credit(ACCOUNT_ID, creditAmount, TX_ID);


        verify(accountRepository, never()).findById(anyLong());
        verify(accountRepository, never()).save(any());
        verify(appliedTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fail credit due to inactive account")
    void testCredit_InactiveAccount() {
        BigDecimal creditAmount = BigDecimal.valueOf(100);
        closedAccount.setStatusAccount(StatusAccount.CLOSED);

        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(TX_ID, closedAccount.getId())).thenReturn(false);
        when(accountRepository.findByIdWithLock(closedAccount.getId())).thenReturn(Optional.of(closedAccount));

        assertThrows(BadRequestException.class,
                () -> accountService.credit(closedAccount.getId(), creditAmount, TX_ID),
                "Expected BadRequestException for Inactive Account");

        verify(accountRepository, never()).save(any());
    }
    @Test
    @DisplayName("Debit: Should throw exception when funds are insufficient")
    void debit_InsufficientFunds_ThrowsBadRequestException() {
        activeAccount.setBalance(new BigDecimal("50.00"));
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));
        when(appliedTransactionRepository.existsByTransactionIdAndAccountId(anyLong(), anyLong())).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                accountService.debit(ACCOUNT_ID, new BigDecimal("100.00"), TX_ID)
        );
    }
}

