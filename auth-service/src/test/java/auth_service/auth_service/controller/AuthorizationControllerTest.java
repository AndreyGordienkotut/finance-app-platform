package auth_service.auth_service.controller;

import auth_service.auth_service.config.JwtAuthFilter;
import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.service.AuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.core.exception.GlobalExceptionHandler;
import core.core.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthorizationController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthorizationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationService authService;

    @MockBean
    private JwtAuthFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private AuthenticationResponseDto authResponse;

    @BeforeEach
    void setUp() {
        authResponse = AuthenticationResponseDto.builder()
                .token("access-token")
                .refreshToken("refresh-token")
                .userId(1L)
                .email("test@mail.com")
                .build();
    }
    @Test
    @DisplayName("POST /register: Success -> 200 OK")
    void register_Success_Returns200() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("test@mail.com", "password", "user");
        when(authService.register(any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(jsonPath("$.email").value("test@mail.com"));
    }

    @Test
    @DisplayName("POST /authenticate: Invalid Credentials -> 401 Unauthorized")
    void authenticate_Invalid_Returns401() throws Exception {
        AuthenticationRequestDto request = new AuthenticationRequestDto("wrong@mail.com", "pass");
        when(authService.authenticate(any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password."));

        mockMvc.perform(post("/api/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("POST /register: Validation Error (Empty Email) -> 400 Bad Request")
    void register_ValidationError_Returns400() throws Exception {
        RegisterRequestDto invalidRequest = new RegisterRequestDto("", "pass", "user");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /logout: Success -> 204 No Content")
    void logout_Success_Returns204() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("some-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout("some-token");
    }
}
