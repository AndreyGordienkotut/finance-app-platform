package account_service.account_service.controller;

import account_service.account_service.config.JwtAuthenticationFilter;
import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.service.AccountService;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import core.core.exception.BadRequestException;
import core.core.exception.GlobalExceptionHandler;
import core.core.exception.NotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc
public class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String API_PATH = "/api/account";
    private static final Long USER_ID = 100L;
    private AccountResponseDto activeAccountDto;

    @BeforeEach
    public void setUp() throws ServletException, IOException {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        activeAccountDto = new AccountResponseDto(
                1L, USER_ID, Currency.USD, BigDecimal.ZERO, StatusAccount.ACTIVE, LocalDateTime.now()
        );
    }
    @Test
    @DisplayName("Create: 201 Created — Success")
    @WithMockUser(username = "100")
    void createAccount_Success() throws Exception {
        when(accountService.createAccount(any(AccountRequestDto.class), eq(USER_ID)))
                .thenReturn(activeAccountDto);

        mockMvc.perform(post(API_PATH + "/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("Create: 400 Bad Request — Account Limit")
    @WithMockUser(username = "100")
    void createAccount_LimitReached() throws Exception {
        when(accountService.createAccount(any(), anyLong()))
                .thenThrow(new BadRequestException("You have reached the limit for creating accounts."));

        mockMvc.perform(post(API_PATH + "/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have reached the limit for creating accounts."));
    }
    @Test
    @DisplayName("Close: 400 Bad Request — Non-zero balance")
    @WithMockUser(username = "100")
    void closeAccount_NonZeroBalance() throws Exception {
        when(accountService.closeAccount(anyLong(), eq(USER_ID)))
                .thenThrow(new BadRequestException("Account balance must be zero to close."));

        mockMvc.perform(post(API_PATH + "/1/close")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account balance must be zero to close."));
    }

    @Test
    @DisplayName("Close: 404 Not Found")
    @WithMockUser(username = "100")
    void closeAccount_NotFound() throws Exception {
        when(accountService.closeAccount(eq(999L), anyLong()))
                .thenThrow(new NotFoundException("Account not found"));

        mockMvc.perform(post(API_PATH + "/999/close")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
    @Test
    @DisplayName("Debit: 200 OK — Internal Call")
    @WithMockUser(username = "100")
    void debit_Success() throws Exception {
        mockMvc.perform(post(API_PATH + "/1/debit")
                        .with(csrf())
                        .param("amount", "50.00")
                        .param("transactionId", "123"))
                .andExpect(status().isOk());

        verify(accountService).debit(eq(1L), eq(new BigDecimal("50.00")), eq(123L));
    }

    @Test
    @DisplayName("Debit: 400 — Insufficient funds")
    @WithMockUser(username = "100")
    void debit_InsufficientFunds() throws Exception {
        doThrow(new BadRequestException("Insufficient funds"))
                .when(accountService).debit(anyLong(), any(), anyLong());

        mockMvc.perform(post(API_PATH + "/1/debit")
                        .with(csrf())
                        .param("amount", "1000.00")
                        .param("transactionId", "124"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient funds"));
    }
    @Test
    @DisplayName("Get All: 200 OK — Return Page of Accounts")
    @WithMockUser(username = "100")
    void getAllAccounts_Success() throws Exception {
        List<AccountResponseDto> list = Collections.singletonList(activeAccountDto);
        Page<AccountResponseDto> page = new PageImpl<>(list);

        when(accountService.getAllAccounts(eq(USER_ID), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(API_PATH)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test
    @DisplayName("Get By Id: 200 OK")
    @WithMockUser(username = "100")
    void getAccountById_Success() throws Exception {
        when(accountService.getAccountById(1L)).thenReturn(activeAccountDto);

        mockMvc.perform(get(API_PATH + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    @DisplayName("Get By Id: 404 Not Found")
    @WithMockUser(username = "100")
    void getAccountById_NotFound() throws Exception {
        when(accountService.getAccountById(999L))
                .thenThrow(new NotFoundException("Account not found"));

        mockMvc.perform(get(API_PATH + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }
}
