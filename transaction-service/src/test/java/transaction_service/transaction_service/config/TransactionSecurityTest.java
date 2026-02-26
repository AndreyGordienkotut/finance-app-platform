package transaction_service.transaction_service.config;

import core.core.dto.AuthenticatedUser;
import core.core.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import transaction_service.transaction_service.controller.TransactionController;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.TransactionService;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc()
class TransactionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/transaction/transfers - Anonymous user should be unauthorized")
    void transfer_Anonymous_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/transactions/transfers - Authenticated user should be allowed to call service")
    @WithMockUser(username = "100")
    void transfer_Authenticated_ShouldSucceed() throws Exception {

        AuthenticatedUser principal = new AuthenticatedUser(
                100L,
                "test@mail.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                );

        when(transactionService.transfer(any(), anyLong(), anyString()))
                .thenReturn(new TransactionResponseDto());

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .with(authentication(auth))
                        .with(csrf())
                        .header("Idempotency-Key", "some-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":1,\"targetAccountId\":2,\"amount\":100}"))
                .andExpect(status().isOk());
    }
}