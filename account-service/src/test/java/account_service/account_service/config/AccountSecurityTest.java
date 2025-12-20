package account_service.account_service.config;

import account_service.account_service.controller.AccountController;
import account_service.account_service.service.AccountService;
import core.core.dto.AccountResponseDto;
import core.core.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc()
class AccountSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String API_CREATE = "/api/account/create";
    private static final String API_DEBIT = "/api/account/1/debit";

    @BeforeEach
    void setUp() throws ServletException, IOException {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("POST " + API_CREATE + " - Anonymous user should be unauthorized")
    void createAccount_Anonymous_ShouldReturn401Or403() throws Exception {

        mockMvc.perform(post(API_CREATE)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST " + API_CREATE + " - Authenticated user with USER role should succeed")
    @WithMockUser(username = "100", roles = "USER")
    void createAccount_UserRole_ShouldReturnCreated() throws Exception {
        when(accountService.createAccount(any(), anyLong()))
                .thenReturn(mock(AccountResponseDto.class));

        mockMvc.perform(post(API_CREATE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST " + API_DEBIT + " - User with USER role should be forbidden (SYSTEM role required)")
    @WithMockUser(username = "100", roles = "USER")
    void debit_UserRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post(API_DEBIT)
                        .with(csrf())
                        .param("amount", "100")
                        .param("transactionId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST " + API_DEBIT + " - Internal service with SYSTEM role should succeed")
    @WithMockUser(username = "system-service", roles = "SYSTEM")
    void debit_SystemRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post(API_DEBIT)
                        .with(csrf())
                        .param("amount", "100")
                        .param("transactionId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/** - Authenticated user without ADMIN role should be denied")
    @WithMockUser
    void anyOtherPath_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }
}