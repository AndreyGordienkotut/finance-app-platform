package auth_service.auth_service.service;
import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.model.EmailVerification;
import core.core.exception.*;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.model.RefreshToken;
import auth_service.auth_service.model.Role;
import auth_service.auth_service.model.Users;
import auth_service.auth_service.repository.EmailVerificationTokensRepository;
import auth_service.auth_service.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
public class AuthorizationServiceTest {
    @Mock
    private UsersRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailVerificationTokensRepository emailVerificationTokensRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private AuthorizationService authorizationService;

    private RegisterRequestDto regDto;
    private Users testUser;

    @BeforeEach
    void setUp() {
        regDto = new RegisterRequestDto("test@mail.com", "pass123", "cool_user");
        testUser = new Users();
        testUser.setId(1L);
        testUser.setEmail("test@mail.com");
        testUser.setUsername("cool_user");
        testUser.setVerified(true);
    }

    @Test
    @DisplayName("Register: Should encode password and save verification token")
    void register_Success_FullSideEffects() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("hash_pass");

        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        when(jwtService.generateToken(anyMap(), any())).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(anyLong())).thenReturn(RefreshToken.builder().token("rf").build());

        authorizationService.register(regDto);

        ArgumentCaptor<Users> userCaptor = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("hash_pass", userCaptor.getValue().getPassword());
        assertEquals("test@mail.com", userCaptor.getValue().getUsername());
        verify(emailVerificationTokensRepository).save(any(EmailVerification.class));
    }
    @Test
    @DisplayName("Register: Should not save anything if user already exists")
    void register_DuplicateEmail_SideEffects() {
        when(userRepository.findByEmail(regDto.getEmail())).thenReturn(Optional.of(testUser));

        assertThrows(UserAlreadyExistsException.class, () ->
                authorizationService.register(regDto)
        );

        verify(userRepository, never()).save(any());
        verify(emailVerificationTokensRepository, never()).save(any());
        verify(refreshTokenService, never()).createRefreshToken(anyLong());
    }
    @Test
    @DisplayName("Authenticate: Success path")
    void authenticate_Success() {
        AuthenticationRequestDto authDto = new AuthenticationRequestDto("test@mail.com", "pass123");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(anyMap(), eq(testUser))).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(RefreshToken.builder().token("rf").build());

        var response = authorizationService.authenticate(authDto);

        assertNotNull(response);
        assertEquals("jwt", response.getToken());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    @DisplayName("Authenticate: Should throw BadRequest if email not verified")
    void authenticate_NotVerified_ThrowsException() {
        testUser.setVerified(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () ->
                authorizationService.authenticate(new AuthenticationRequestDto("test@mail.com", "123"))
        );
    }

    @Test
    @DisplayName("Authenticate: Should throw InvalidCredentials if AuthenticationManager fails")
    void authenticate_BadCredentials_ThrowsException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        doThrow(new BadCredentialsException("error"))
                .when(authenticationManager).authenticate(any());

        assertThrows(InvalidCredentialsException.class, () ->
                authorizationService.authenticate(new AuthenticationRequestDto("test@mail.com", "wrong"))
        );
    }
    @Test
    @DisplayName("Authenticate: Should throw Exception if user not found")
    void authenticate_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authorizationService.authenticate(new AuthenticationRequestDto("fake@mail.com", "123"))
        );
    }

    @Test
    @DisplayName("Refresh: Should maintain consistent Claims (userId)")
    void refreshToken_Success_ConsistentClaims() {
        RefreshToken rf = RefreshToken.builder().token("old_rf").user(testUser).build();
        when(refreshTokenService.findByToken("old_rf")).thenReturn(Optional.of(rf));
        when(jwtService.generateToken(anyMap(), eq(testUser))).thenReturn("new_jwt");

        authorizationService.refreshToken(new RefreshTokenRequestDto("old_rf"));
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateToken(claimsCaptor.capture(), eq(testUser));
        assertEquals(1L, claimsCaptor.getValue().get( "userId"));
    }
    @Test
    @DisplayName("Refresh: Should throw Exception if token not found")
    void refreshToken_NotFound_ThrowsException() {
        when(refreshTokenService.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                authorizationService.refreshToken(new RefreshTokenRequestDto("fake"))
        );
    }

    @Test
    @DisplayName("Refresh: Should fail if token is expired")
    void refreshToken_Expired_ThrowsException() {
        RefreshToken rf = RefreshToken.builder().token("expired").user(testUser).build();
        when(refreshTokenService.findByToken("expired")).thenReturn(Optional.of(rf));

        doThrow(new BadRequestException("Token expired"))
                .when(refreshTokenService).verifyExpiration(rf);

        assertThrows(BadRequestException.class, () ->
                authorizationService.refreshToken(new RefreshTokenRequestDto("expired"))
        );
    }
    @Test
    @DisplayName("Refresh: Should not recreate RefreshToken (reuse old one)")
    void refreshToken_Success_ShouldNotRecreateToken() {
        RefreshToken rf = RefreshToken.builder().token("existing_refresh_token").user(testUser).build();
        when(refreshTokenService.findByToken(anyString())).thenReturn(Optional.of(rf));
        when(jwtService.generateToken(anyMap(), eq(testUser))).thenReturn("new_access_token");

        authorizationService.refreshToken(new RefreshTokenRequestDto("existing_refresh_token"));

        verify(refreshTokenService, never()).createRefreshToken(anyLong());
        verify(refreshTokenService).findByToken("existing_refresh_token");
    }
    @Test
    @DisplayName("Logout: Should delete token if exists")
    void logout_TokenExists_DeletesIt() {
        RefreshToken rf = new RefreshToken();
        when(refreshTokenService.findByToken("token_to_delete")).thenReturn(Optional.of(rf));
        authorizationService.logout("token_to_delete");

        verify(refreshTokenService).delete(rf);
    }

    @Test
    @DisplayName("Logout: Should do nothing if token not found")
    void logout_TokenNotFound_DoesNothing() {
        when(refreshTokenService.findByToken(anyString())).thenReturn(Optional.empty());

        authorizationService.logout("fake_token");

        verify(refreshTokenService, never()).delete(any());
    }
}
