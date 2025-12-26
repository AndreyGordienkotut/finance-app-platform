package account_service.account_service.config;

import account_service.account_service.dto.AccountRequestDto;
import account_service.account_service.service.AccountService;
import core.core.config.JwtClaims;
import core.core.dto.AccountResponseDto;
import core.core.enums.Currency;
import core.core.enums.StatusAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${JWT_SECRET:1234mSlongtdmVyeS1zdHJvbmctc2VjcmV0LWtleS1mb3Itand0LXNpZ25pbmc=}")
    private String secret;

    @MockBean
    private AccountService accountService;
    @MockBean
    private UserDetailsService userDetailsService;
    private String generateToken(Long userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaims.USER_ID, userId);
        claims.put(JwtClaims.ROLES, roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("test@mail.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    @Test
    @DisplayName("GET /api/account -> 200 OK for Authenticated User")
    void getAccounts_Success() throws Exception {
        Long userId = 1L;
        String token = generateToken(userId, List.of("ROLE_USER"));

        when(accountService.getAllAccounts(eq(userId), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/account")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/account/create -> 201 Created")
    void createAccount_Success() throws Exception {
        Long userId = 1L;
        String token = generateToken(userId, List.of("ROLE_USER"));

        String jsonRequest = "{\"currency\":\"USD\"}";

        AccountResponseDto mockResponse = AccountResponseDto.builder()
                .id(100L)
                .userId(userId)
                .currency(Currency.USD)
                .balance(BigDecimal.ZERO)
                .status(StatusAccount.ACTIVE)
                .createAt(LocalDateTime.now())
                .build();

        when(accountService.createAccount(any(AccountRequestDto.class), eq(userId)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/account/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("SYSTEM Role Required for Debit/Credit -> 403 for regular User")
    void debit_UserRole_ShouldReturn403() throws Exception {
        String token = generateToken(1L, List.of("ROLE_USER"));

        mockMvc.perform(post("/api/account/1/debit")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "100.0")
                        .param("transactionId", "123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SYSTEM Role Access to Debit -> 200 OK")
    void debit_SystemRole_ShouldReturn200() throws Exception {
        String token = generateToken(99L, List.of("ROLE_SYSTEM"));

        mockMvc.perform(post("/api/account/1/debit")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "100.0")
                        .param("transactionId", "123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Access Denied when closing someone else's account -> 403")
    void closeAccount_Forbidden() throws Exception {
        Long myUserId = 1L;
        Long foreignAccountId = 55L;
        String token = generateToken(myUserId, List.of("ROLE_USER"));

        when(accountService.closeAccount(eq(foreignAccountId), eq(myUserId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not your account"));

        mockMvc.perform(post("/api/account/" + foreignAccountId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}