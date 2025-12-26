package auth_service.auth_service.service;

import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import core.core.config.JwtClaims;
import core.core.exception.*;
import auth_service.auth_service.model.EmailVerification;
import auth_service.auth_service.model.RefreshToken;
import auth_service.auth_service.model.Role;
import auth_service.auth_service.model.Users;
import auth_service.auth_service.repository.EmailVerificationTokensRepository;
import auth_service.auth_service.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {
    private final UsersRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationTokensRepository emailVerificationTokensRepository;
    private final RefreshTokenService refreshTokenService;
    @Transactional
    public AuthenticationResponseDto register(RegisterRequestDto registerRequestDto) {
        if(userRepository.findByEmail(registerRequestDto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("User with this email already exists.");
        }
        Users user = new Users();
        user.setEmail(registerRequestDto.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequestDto.getPassword()));
        user.setUsername(registerRequestDto.getUsername()); //change
        user.setRole(Role.CLIENT);
        user.setVerified(false);
        Users savedUser = userRepository.save(user);
        String token = UUID.randomUUID().toString();
        EmailVerification emailVerificationTokens = EmailVerification.builder()
                .user(savedUser)
                .token(token)
                .used(false)
                .expiryAt(LocalDateTime.now().plusHours(24))
                .build();
        emailVerificationTokensRepository.save(emailVerificationTokens);
//        Map<String, Object> extraClaims = Map.of("userId", savedUser.getId());
        String jwtAccessToken = jwtService.generateToken(user, user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());

        return createAuthResponse(savedUser, jwtAccessToken, refreshToken.getToken());
    }
    @Transactional
    public AuthenticationResponseDto authenticate(AuthenticationRequestDto authenticationRequestDto) {
        Users user = userRepository.findByEmail(authenticationRequestDto.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!user.isVerified()) {
            throw new BadRequestException("Email is not verified. Please check your inbox.");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authenticationRequestDto.getEmail(),
                            authenticationRequestDto.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            System.err.println("Authentication failed for user " + authenticationRequestDto.getEmail() + ": " + e.getMessage());
            throw new InvalidCredentialsException("Invalid email or password.");
        }
        String jwtAccessToken = jwtService.generateToken(user, user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return createAuthResponse(user, jwtAccessToken, refreshToken.getToken());
    }
    @Transactional
    public AuthenticationResponseDto refreshToken(RefreshTokenRequestDto request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Refresh token is not in database!"));

        refreshTokenService.verifyExpiration(refreshToken);

        Users user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user, user.getId());

        return createAuthResponse(user, newAccessToken, refreshToken.getToken());
    }
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.findByToken(refreshToken)
                .ifPresent(refreshTokenService::delete);
    }

    private AuthenticationResponseDto createAuthResponse(Users user, String accessToken, String refreshToken) {
        return AuthenticationResponseDto.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }



}
