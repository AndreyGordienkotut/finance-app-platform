package transaction_service.transaction_service.controller;


import core.core.dto.AuthenticatedUser;
import core.core.enums.Currency;
import core.core.exception.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import transaction_service.transaction_service.config.JwtAuthenticationFilter;
import transaction_service.transaction_service.dto.DepositRequestDto;
import transaction_service.transaction_service.dto.TransactionRequestDto;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.dto.WithdrawRequestDto;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.TransactionService;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import( GlobalExceptionHandler.class)
@AutoConfigureMockMvc
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private TransactionService transactionService;
    @MockBean
    private  ExchangeRateService exchangeRateService;

    private static final String API_PATH = "/api/v1/transactions";
    private static final String IDEMPOTENCY_KEY = "test-key-123";

    private TransactionResponseDto successTransferResponse;
    private TransactionResponseDto successDepositResponse;
    private TransactionResponseDto successWithdrawResponse;

    private String transferJson;
    private String depositJson;
    private String withdrawJson;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        successTransferResponse = TransactionResponseDto.builder()
                .id(1L)
                .sourceAccountId(1L)
                .targetAccountId(2L)
                .amount(BigDecimal.valueOf(100))
                .targetAmount(BigDecimal.valueOf(100))
                .exchangeRate(BigDecimal.ONE)
                .currency(Currency.USD)
                .status(Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .transactionType(TransactionType.TRANSFER)
                .category(null)
                .build();

        successDepositResponse = TransactionResponseDto.builder()
                .id(2L)
                .targetAccountId(1L)
                .amount(BigDecimal.valueOf(50))
                .targetAmount(BigDecimal.valueOf(50))
                .exchangeRate(BigDecimal.ONE)
                .currency(Currency.USD)
                .status(Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .transactionType(TransactionType.DEPOSIT)
                .build();

        successWithdrawResponse = TransactionResponseDto.builder()
                .id(3L)
                .sourceAccountId(1L)
                .amount(BigDecimal.valueOf(20))
                .targetAmount(BigDecimal.valueOf(20))
                .exchangeRate(BigDecimal.ONE)
                .currency(Currency.USD)
                .status(Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .transactionType(TransactionType.WITHDRAW)
                .build();

        transferJson = "{\"sourceAccountId\":1,\"targetAccountId\":2,\"amount\":100}";
        depositJson = "{\"targetAccountId\":1,\"amount\":50}";
        withdrawJson = "{\"sourceAccountId\":1,\"amount\":20}";
    }

    @Test
    @DisplayName("POST /transfers: Success -> 200 OK")
    @WithMockUser(username = "100")
    void testTransferSuccess() throws Exception {
        when(transactionService.transfer(any(TransactionRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
                .thenReturn(successTransferResponse);
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
        mockMvc.perform(post(API_PATH + "/transfers")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson))
                .andExpect(status().isOk());

    }

    @Test
    @DisplayName("POST /transfers: Missing Principal -> 401 Unauthorized")
    void testTransferUnauthorized() throws Exception {
        mockMvc.perform(post(API_PATH + "/transfers")
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /transfers: Invalid JSON (Amount missing) -> 400 Bad Request (Validation)")
    @WithMockUser(username = "100")
    void testTransferInvalidJson() throws Exception {
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
        mockMvc.perform(post(API_PATH + "/transfers")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":1,\"targetAccountId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfers: Service throws BadRequest -> 400 Bad Request")
    @WithMockUser(username = "100")
    void testTransferServiceBadRequest() throws Exception {
        when(transactionService.transfer(any(), anyLong(), anyString()))
                .thenThrow(new BadRequestException("Insufficient funds"));
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
        mockMvc.perform(post(API_PATH + "/transfers")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"))
                .andExpect(jsonPath("$.path").value(API_PATH + "/transfers"));
    }

    @Test
    @DisplayName("POST /transfers: Missing Idempotency-Key -> 400 Bad Request")
    @WithMockUser(username = "100")
    void testTransferMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post(API_PATH + "/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson))
                .andExpect(status().isBadRequest());
    }
    @Test
    @DisplayName("POST /deposits: Success -> 200 OK")
    void testDepositSuccess() throws Exception {
        when(transactionService.deposit(any(DepositRequestDto.class), eq(IDEMPOTENCY_KEY), eq(100L)))
                .thenReturn(successDepositResponse);
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

        mockMvc.perform(post(API_PATH + "/deposits")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /deposits: Missing Principal -> 401 Unauthorized")
    void testDepositUnauthorized() throws Exception {
        mockMvc.perform(post(API_PATH + "/deposits")
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /deposits: Service throws BadRequest -> 400 Bad Request")
    @WithMockUser(username = "100")
    void testDepositServiceBadRequest() throws Exception {
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
        when(transactionService.deposit(any(DepositRequestDto.class), eq(IDEMPOTENCY_KEY), eq(100L)))
                .thenThrow(new BadRequestException("Target account is closed."));

        mockMvc.perform(post(API_PATH + "/deposits")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("POST /withdrawals: Success -> 200 OK")
    @WithMockUser(username = "100")
    void testWithdrawSuccess() throws Exception {
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
        when(transactionService.withdraw(any(WithdrawRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
                .thenReturn(successWithdrawResponse);
        mockMvc.perform(post(API_PATH + "/withdrawals")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionType").value("WITHDRAW"));
    }

    @Test
    @DisplayName("POST /withdrawals: Missing Principal -> 401 Unauthorized")
    void testWithdrawUnauthorized() throws Exception {
        mockMvc.perform(post(API_PATH + "/withdrawals")
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /withdrawals: Service throws BadRequest -> 400 Bad Request")
    @WithMockUser(username = "100")
    void testWithdrawServiceBadRequest() throws Exception {
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
        when(transactionService.withdraw(any(WithdrawRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new BadRequestException("Not enough money for withdrawal."));

        mockMvc.perform(post(API_PATH + "/withdrawals")
                        .with(csrf())
                        .with(authentication(auth))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawJson))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("GET: Success -> 200 OK")
    @WithMockUser(username = "100")
    void testGetHistorySuccess() throws Exception {
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
        List<TransactionResponseDto> txList = List.of(successTransferResponse, successDepositResponse);
        PageImpl<TransactionResponseDto> page = new PageImpl<>(txList);

        when(transactionService.getHistory(anyLong(), any(Pageable.class), anyLong()))
                .thenReturn(page);

        mockMvc.perform(get(API_PATH)
                        .param("accountId", "1")
                        .param("page", "0")
                        .param("size", "10")
                        .with(csrf())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET: Missing Principal -> 401 Unauthorized")
    void testGetHistoryUnauthorized() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .param("accountId", "1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))

                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET : Not owner / Account not found -> 404 Not Found")
    @WithMockUser(username = "105")
    void testGetHistoryNotOwner() throws Exception {
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
        when(transactionService.getHistory(anyLong(), any(Pageable.class), anyLong()))
                .thenThrow(new NotFoundException("Account not found or access denied for this user."));

        mockMvc.perform(get(API_PATH )
                        .param("accountId", "1")
                        .with(csrf())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

}
