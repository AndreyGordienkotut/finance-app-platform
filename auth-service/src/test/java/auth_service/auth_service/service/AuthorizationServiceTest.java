package auth_service.auth_service.service;
import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.mapper.AuthMapper;
import auth_service.auth_service.model.TelegramVerification;
import auth_service.auth_service.repository.TelegramVerificationRepository;
import core.core.exception.*;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.model.RefreshToken;
import auth_service.auth_service.model.Users;
import auth_service.auth_service.repository.UsersRepository;
import core.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private AuthMapper authMapper;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TelegramVerificationRepository telegramVerificationRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private AuthorizationService authorizationService;

    private RegisterRequestDto regDto;
    private Users testUser;
    private AuthenticationResponseDto authResponseDto;
    @BeforeEach
    void setUp() {
        regDto = new RegisterRequestDto("test@mail.com", "pass123", "cool_user");
        testUser = new Users();
        testUser.setId(1L);
        testUser.setEmail("test@mail.com");
        testUser.setUsername("cool_user");
        testUser.setVerified(true);
        authResponseDto = AuthenticationResponseDto.builder()
                .token("jwt")
                .refreshToken("rf")
                .userId(1L)
                .username("cool_user")
                .email("test@mail.com")
                .verificationCode("847291")
                .build();
    }

    @Test
    @DisplayName("Register: Success saves user and telegram verification")
    void register_Success_FullSideEffects() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("hash_pass");

        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        when(jwtService.generateToken(any(UserDetails.class), anyLong())).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(anyLong())).thenReturn(RefreshToken.builder().token("rf").build());
        when(authMapper.toAuthResponse(any(Users.class), anyString(), anyString(), anyString()))
                .thenReturn(authResponseDto);

        AuthenticationResponseDto result = authorizationService.register(regDto);

        ArgumentCaptor<Users> userCaptor = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("hash_pass", userCaptor.getValue().getPassword());
        assertFalse(userCaptor.getValue().isVerified());

        verify(telegramVerificationRepository).save(any(TelegramVerification.class));
        assertNotNull(result.getVerificationCode());
    }
    @Test
    @DisplayName("Register: verification code is 6 digits")
    void register_VerificationCode_Is6Digits() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> {
            Users u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(), anyLong())).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(anyLong()))
                .thenReturn(RefreshToken.builder().token("rf").build());

        ArgumentCaptor<TelegramVerification> captor =
                ArgumentCaptor.forClass(TelegramVerification.class);
        when(authMapper.toAuthResponse(any(), anyString(), anyString(), anyString()))
                .thenReturn(authResponseDto);

        authorizationService.register(regDto);

        verify(telegramVerificationRepository).save(captor.capture());
        String code = captor.getValue().getCode();
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    @DisplayName("Register: Should not save anything if user already exists")
    void register_DuplicateEmail_SideEffects() {
        when(userRepository.findByEmail(regDto.getEmail())).thenReturn(Optional.of(testUser));

        assertThrows(UserAlreadyExistsException.class, () ->
                authorizationService.register(regDto)
        );

        verify(userRepository, never()).save(any());
        verify(telegramVerificationRepository, never()).save(any());
        verify(refreshTokenService, never()).createRefreshToken(anyLong());
    }
    @Test
    @DisplayName("Authenticate: Success path")
    void authenticate_Success() {
        AuthenticationRequestDto authDto = new AuthenticationRequestDto("test@mail.com", "pass123");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(UserDetails.class), anyLong())).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(RefreshToken.builder().token("rf").build());
        when(authMapper.toAuthResponse(any(Users.class), anyString(), anyString(), isNull()))
                .thenReturn(authResponseDto);
        var response = authorizationService.authenticate(authDto);

        assertNotNull(response);
        assertEquals("jwt", response.getToken());
        verify(authenticationManager).authenticate(any());
    }
    @Test
    @DisplayName("Authenticate: verificationCode is null in response")
    void authenticate_VerificationCode_IsNull() {
        AuthenticationRequestDto authDto =
                new AuthenticationRequestDto("test@mail.com", "pass123");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(), anyLong())).thenReturn("jwt");
        when(refreshTokenService.createRefreshToken(anyLong()))
                .thenReturn(RefreshToken.builder().token("rf").build());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        when(authMapper.toAuthResponse(any(), anyString(), anyString(), codeCaptor.capture()))
                .thenReturn(authResponseDto);

        authorizationService.authenticate(authDto);

        assertNull(codeCaptor.getValue());
    }

    @Test
    @DisplayName("Authenticate: Should throw BadRequest if not verified")
    void authenticate_NotVerified_ThrowsException() {
        testUser.setVerified(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () ->
                authorizationService.authenticate(
                        new AuthenticationRequestDto("test@mail.com", "123")));
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
    @DisplayName("verifyByCode: Success sets verified=true, returns userId")
    void verifyByCode_Success_ReturnsUserId() {
        TelegramVerification verification = TelegramVerification.builder()
                .code("847291")
                .used(false)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .user(testUser)
                .build();

        when(telegramVerificationRepository.findByCodeAndUsedFalse("847291"))
                .thenReturn(Optional.of(verification));

        Long userId = authorizationService.verifyByCode("847291");

        assertEquals(1L, userId);
        assertTrue(verification.isUsed());
        assertTrue(testUser.isVerified());
        verify(telegramVerificationRepository).save(verification);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("verifyByCode: Invalid code throws BadRequestException")
    void verifyByCode_InvalidCode_Throws() {
        when(telegramVerificationRepository.findByCodeAndUsedFalse("000000"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                authorizationService.verifyByCode("000000"));
    }

    @Test
    @DisplayName("verifyByCode: Expired code throws BadRequestException")
    void verifyByCode_ExpiredCode_Throws() {
        Users unverifiedUser = new Users();
        unverifiedUser.setId(2L);
        unverifiedUser.setVerified(false);
        TelegramVerification verification = TelegramVerification.builder()
                .code("847291")
                .used(false)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .user(unverifiedUser)
                .build();

        when(telegramVerificationRepository.findByCodeAndUsedFalse("847291"))
                .thenReturn(Optional.of(verification));

        assertThrows(BadRequestException.class, () ->
                authorizationService.verifyByCode("847291"));
        assertFalse(unverifiedUser.isVerified());
    }

    @Test
    @DisplayName("Refresh: Should maintain consistent Claims (userId)")
    void refreshToken_Success_ConsistentClaims() {
        RefreshToken rf = RefreshToken.builder().token("old_rf").user(testUser).build();
        when(refreshTokenService.findByToken("old_rf")).thenReturn(Optional.of(rf));
        when(jwtService.generateToken(any(UserDetails.class), anyLong())).thenReturn("jwt");
        when(authMapper.toAuthResponse(any(Users.class), anyString(), anyString(), isNull()))
                .thenReturn(authResponseDto);
        authorizationService.refreshToken(new RefreshTokenRequestDto("old_rf"));
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);

        verify(jwtService).generateToken(eq(testUser), userIdCaptor.capture());

        assertEquals(testUser.getId(), userIdCaptor.getValue());
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
        when(jwtService.generateToken(any(UserDetails.class), anyLong())).thenReturn("jwt");
        when(authMapper.toAuthResponse(any(), anyString(), anyString(), isNull()))
                .thenReturn(authResponseDto);
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
