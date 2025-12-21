package auth_service.auth_service.config;

import auth_service.auth_service.controller.AuthorizationController;
import auth_service.auth_service.repository.UsersRepository;
import auth_service.auth_service.service.AuthorizationService;
import auth_service.auth_service.service.JwtService;
import core.core.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthorizationController.class, AuthSecurityTest.TestSecurityController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class AuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationService authService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private UsersRepository usersRepository;

    @RestController
    static class TestSecurityController {
        @GetMapping("/api/admin/users")
        public ResponseEntity<Void> adminTest() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/user/profile")
        public ResponseEntity<Void> userTest() {
            return ResponseEntity.ok().build();
        }
    }

    @BeforeEach
    void setUp() throws ServletException, IOException {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("SEC: POST /api/auth/register - Should be permitted for everyone")
    void register_Anonymous_ShouldBePermitted() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@mail.com\", \"password\":\"1234567\", \"username\":\"user\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC: GET /api/user/profile - Should return 403 for Anonymous")
    void userProfile_Anonymous_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC: GET /api/admin/users - Forbidden for USER")
    @WithMockUser(roles = "USER")
    void adminUsers_UserRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}