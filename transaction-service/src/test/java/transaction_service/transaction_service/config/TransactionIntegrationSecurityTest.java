package transaction_service.transaction_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import transaction_service.transaction_service.service.ExchangeRateService;
import transaction_service.transaction_service.service.TransactionService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionIntegrationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${JWT_SECRET:1234mSlongtdmVyeS1zdHJvbmctc2VjcmV0LWtleS1mb3Itand0LXNpZ25pbmc=}")
    private String secret;

    @MockBean
    private TransactionService transactionService;
    @MockBean
    private ExchangeRateService exchangeRateService;

    @MockBean
    private UserDetailsService userDetailsService;

    private static final String BASE_PATH = "/api/v1/transactions";
    private String generateRealToken(Long userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        claims.put("sub", String.valueOf(userId));

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }
    @Test
    @DisplayName("GET /history Without JWT -> 403 Forbidden")
    void history_NoToken_ShouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("accountId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /history Valid JWT -> 200 OK")
    void history_ValidToken_ShouldReturn200() throws Exception {
        Long mockUserId = 1L;
        String token = generateRealToken(mockUserId, List.of("ROLE_USER"));

        when(transactionService.getHistory(eq(1L), any(Pageable.class), eq(mockUserId)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", "1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /history With Invalid Sign -> 403 Forbidden")
    void history_InvalidSign_ShouldReturn403() throws Exception {
        String badToken = Jwts.builder()
                .setSubject("1")
                .signWith(Keys.hmacShaKeyFor("wrong-secret-length-must-be-very-long-and-secure-123".getBytes()))
                .compact();

        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + badToken)
                        .param("accountId", "1"))
                .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("IT: User tries to access someone else's account -> 403 Forbidden")
    void getHistory_OtherUserAccount_ShouldReturn403() throws Exception {
        Long myUserId = 1L;
        Long foreignAccountId = 999L;
        String token = generateRealToken(myUserId, List.of("ROLE_USER"));

        when(transactionService.getHistory(eq(foreignAccountId), any(), eq(myUserId)))
                .thenThrow(new AccessDeniedException("You do not own this account"));

        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", foreignAccountId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }
}