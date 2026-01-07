//package transaction_service.transaction_service.controller;
//
//
//import core.core.enums.Currency;
//import core.core.exception.*;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.servlet.MockMvc;
//import transaction_service.transaction_service.config.JwtAuthenticationFilter;
//import transaction_service.transaction_service.dto.DepositRequestDto;
//import transaction_service.transaction_service.dto.TransactionRequestDto;
//import transaction_service.transaction_service.dto.TransactionResponseDto;
//import transaction_service.transaction_service.dto.WithdrawRequestDto;
//import transaction_service.transaction_service.model.Status;
//import transaction_service.transaction_service.model.TypeTransaction;
//import transaction_service.transaction_service.service.TransactionService;
//
//
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(TransactionController.class)
//@Import( GlobalExceptionHandler.class)
//@AutoConfigureMockMvc
//public class TransactionControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//    @MockBean
//    private JwtAuthenticationFilter jwtAuthenticationFilter;
//    @MockBean
//    private TransactionService transactionService;
//
//
//    private static final String API_PATH = "/api/transaction";
////    private static final String USER_ID = "100";
//    private static final String IDEMPOTENCY_KEY = "test-key-123";
//
//    private TransactionResponseDto successTransferResponse;
//    private TransactionResponseDto successDepositResponse;
//    private TransactionResponseDto successWithdrawResponse;
//
//    private String transferJson;
//    private String depositJson;
//    private String withdrawJson;
//
//    @BeforeEach
//    void setUp() throws ServletException, IOException {
//        doAnswer(invocation -> {
//            HttpServletRequest request = invocation.getArgument(0);
//            HttpServletResponse response = invocation.getArgument(1);
//            FilterChain chain = invocation.getArgument(2);
//            chain.doFilter(request, response);
//            return null;
//        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
//
//        successTransferResponse = new TransactionResponseDto(
//                1L, 1L, 2L, BigDecimal.valueOf(100), Currency.USD,
//                Status.COMPLETED, LocalDateTime.now(), null, LocalDateTime.now(), TypeTransaction.TRANSFER
//        );
//        successDepositResponse = new TransactionResponseDto(
//                2L, null, 1L, BigDecimal.valueOf(50), Currency.USD,
//                Status.COMPLETED, LocalDateTime.now(), null, LocalDateTime.now(), TypeTransaction.DEPOSIT
//        );
//        successWithdrawResponse = new TransactionResponseDto(
//                3L, 1L, null, BigDecimal.valueOf(20), Currency.USD,
//                Status.COMPLETED, LocalDateTime.now(), null, LocalDateTime.now(), TypeTransaction.WITHDRAW
//        );
//
//        transferJson = "{\"sourceAccountId\":1,\"targetAccountId\":2,\"amount\":100}";
//        depositJson = "{\"targetAccountId\":1,\"amount\":50}";
//        withdrawJson = "{\"sourceAccountId\":1,\"amount\":20}";
//    }
//
//    @Test
//    @DisplayName("POST /transfer: Success -> 200 OK")
//    @WithMockUser(username = "100")
//    void testTransferSuccess() throws Exception {
//        when(transactionService.transfer(any(TransactionRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
//                .thenReturn(successTransferResponse);
//
//        mockMvc.perform(post(API_PATH + "/transfer")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(transferJson))
//                .andExpect(status().isOk());
//
//    }
//
//    @Test
//    @DisplayName("POST /transfer: Missing Principal -> 401 Unauthorized")
//    void testTransferUnauthorized() throws Exception {
//        mockMvc.perform(post(API_PATH + "/transfer")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(transferJson))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    @DisplayName("POST /transfer: Invalid JSON (Amount missing) -> 400 Bad Request (Validation)")
//    @WithMockUser(username = "100")
//    void testTransferInvalidJson() throws Exception {
//        mockMvc.perform(post(API_PATH + "/transfer")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"sourceAccountId\":1,\"targetAccountId\":2}"))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    @DisplayName("POST /transfer: Service throws BadRequest -> 400 Bad Request")
//    @WithMockUser(username = "100")
//    void testTransferServiceBadRequest() throws Exception {
//        when(transactionService.transfer(any(), anyLong(), anyString()))
//                .thenThrow(new BadRequestException("Insufficient funds"));
//
//        mockMvc.perform(post(API_PATH + "/transfer")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(transferJson))
//                .andDo(print())
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.status").value(400))
//                .andExpect(jsonPath("$.error").value("Bad Request"))
//                .andExpect(jsonPath("$.message").value("Insufficient funds"))
//                .andExpect(jsonPath("$.path").value(API_PATH + "/transfer"));
//    }
//
//    @Test
//    @DisplayName("POST /transfer: Missing Idempotency-Key -> 400 Bad Request")
//    @WithMockUser(username = "100")
//    void testTransferMissingIdempotencyKey() throws Exception {
//        mockMvc.perform(post(API_PATH + "/transfer")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(transferJson))
//                .andExpect(status().isBadRequest());
//    }
//    @Test
//    @DisplayName("POST /deposit: Success -> 200 OK")
//    @WithMockUser(username = "100")
//    void testDepositSuccess() throws Exception {
//        when(transactionService.deposit(any(DepositRequestDto.class), eq(IDEMPOTENCY_KEY), eq(100L)))
//                .thenReturn(successDepositResponse);
//
//        mockMvc.perform(post(API_PATH + "/deposit")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(depositJson))
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    @DisplayName("POST /deposit: Missing Principal -> 401 Unauthorized")
//    void testDepositUnauthorized() throws Exception {
//        mockMvc.perform(post(API_PATH + "/deposit")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(depositJson))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    @DisplayName("POST /deposit: Service throws BadRequest -> 400 Bad Request")
//    @WithMockUser(username = "100")
//    void testDepositServiceBadRequest() throws Exception {
//        when(transactionService.deposit(any(DepositRequestDto.class), eq(IDEMPOTENCY_KEY), eq(100L)))
//                .thenThrow(new BadRequestException("Target account is closed."));
//
//        mockMvc.perform(post(API_PATH + "/deposit")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(depositJson))
//                .andExpect(status().isBadRequest());
//    }
//
//
//    @Test
//    @DisplayName("POST /withdraw: Success -> 200 OK")
//    @WithMockUser(username = "100")
//    void testWithdrawSuccess() throws Exception {
//
//        when(transactionService.withdraw(any(WithdrawRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
//                .thenReturn(successWithdrawResponse);
//        mockMvc.perform(post(API_PATH + "/withdraw")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(withdrawJson))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.typeTransaction").value("WITHDRAW"));
//    }
//
//    @Test
//    @DisplayName("POST /withdraw: Missing Principal -> 401 Unauthorized")
//    void testWithdrawUnauthorized() throws Exception {
//        mockMvc.perform(post(API_PATH + "/withdraw")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(withdrawJson))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    @DisplayName("POST /withdraw: Service throws BadRequest -> 400 Bad Request")
//    @WithMockUser(username = "100")
//    void testWithdrawServiceBadRequest() throws Exception {
//        when(transactionService.withdraw(any(WithdrawRequestDto.class), eq(100L), eq(IDEMPOTENCY_KEY)))
//                .thenThrow(new BadRequestException("Not enough money for withdrawal."));
//
//        mockMvc.perform(post(API_PATH + "/withdraw")
//                        .with(csrf())
//                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(withdrawJson))
//                .andExpect(status().isBadRequest());
//    }
//
//
//    @Test
//    @DisplayName("GET /history: Success -> 200 OK")
//    @WithMockUser(username = "100")
//    void testGetHistorySuccess() throws Exception {
//        List<TransactionResponseDto> txList = List.of(successTransferResponse, successDepositResponse);
//        PageImpl<TransactionResponseDto> page = new PageImpl<>(txList);
//
//        when(transactionService.getHistory(anyLong(), any(Pageable.class), anyLong()))
//                .thenReturn(page);
//
//        mockMvc.perform(get(API_PATH + "/history")
//                        .param("accountId", "1")
//                        .param("page", "0")
//                        .param("size", "10")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andDo(print())
//                .andExpect(status().isOk())
//
//                .andExpect(jsonPath("$.content[0].id").value(1))
//                .andExpect(jsonPath("$.totalElements").value(2));
//    }
//
//    @Test
//    @DisplayName("GET /history: Missing Principal -> 401 Unauthorized")
//    void testGetHistoryUnauthorized() throws Exception {
//        mockMvc.perform(get(API_PATH + "/history")
//                        .param("accountId", "1")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON))
//
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    @DisplayName("GET /history: Not owner / Account not found -> 404 Not Found")
//    @WithMockUser(username = "105")
//    void testGetHistoryNotOwner() throws Exception {
//        when(transactionService.getHistory(anyLong(), any(Pageable.class), anyLong()))
//                .thenThrow(new NotFoundException("Account not found or access denied for this user."));
//
//        mockMvc.perform(get(API_PATH + "/history")
//                        .param("accountId", "1")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isNotFound());
//    }
//
//}
