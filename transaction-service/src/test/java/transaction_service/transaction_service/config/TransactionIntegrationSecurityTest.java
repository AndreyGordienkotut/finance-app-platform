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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import transaction_service.transaction_service.service.TransactionService;

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
    private UserDetailsService userDetailsService;

    private String generateRealToken(Long userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        claims.put("sub", "test@mail.com");

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    @Test
    @DisplayName("IT: No JWT -> 403 Forbidden")
    void noToken_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/test/history")
                        .param("accountId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT: Valid JWT + ROLE_USER -> 200 OK")
    void validToken_ShouldReturn200() throws Exception {
        String token = generateRealToken(1L, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/test/test-principal")
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("IT: Valid JWT + Wrong Role -> 403 Forbidden")
    void wrongRole_ShouldReturn403() throws Exception {
        String token = generateRealToken(1L, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/test/admin-stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}