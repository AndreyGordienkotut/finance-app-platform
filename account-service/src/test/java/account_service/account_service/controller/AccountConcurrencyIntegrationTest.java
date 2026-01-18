package account_service.account_service.controller;

import account_service.account_service.AccountServiceApplication;
import account_service.account_service.config.JwtAuthenticationFilter;
import account_service.account_service.model.Account;
import account_service.account_service.repository.AccountRepository;
import account_service.account_service.service.AccountService;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = AccountServiceApplication.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AccountConcurrencyIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @BeforeEach
    void setUp() throws ServletException, IOException {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }
    @Test
    @DisplayName("Concurrency: Parallel debit should prevent double-spend and negative balance")
    void testParallelDebit() throws Exception {
        Account account = Account.builder()
                .userId(1L)
                .balance(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .statusAccount(StatusAccount.ACTIVE)
                .createAt(LocalDateTime.now())
                .build();
        account = accountRepository.saveAndFlush(account);
        Long accId = account.getId();

        BigDecimal debitAmount = new BigDecimal("60.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<Void> threadA = CompletableFuture.runAsync(() ->
                accountService.debit(accId, debitAmount, 1001L), executor);

        CompletableFuture<Void> threadB = CompletableFuture.runAsync(() ->
                accountService.debit(accId, debitAmount, 1002L), executor);

        CompletableFuture.allOf(threadA, threadB)
                .exceptionally(ex -> null)
                .join();

        Account finalAccount = accountRepository.findById(accId).orElseThrow();

        assertEquals(0, new BigDecimal("40.00").compareTo(finalAccount.getBalance()),
                "Balance should be 40.00, meaning only one transaction succeeded");

        executor.shutdown();
    }
}