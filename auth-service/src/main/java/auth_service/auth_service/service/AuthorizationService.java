package auth_service.auth_service.service;

import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.mapper.AuthMapper;
import auth_service.auth_service.model.TelegramVerification;
import auth_service.auth_service.repository.TelegramVerificationRepository;
import core.core.exception.*;
import core.core.security.JwtService;
import auth_service.auth_service.model.RefreshToken;
import auth_service.auth_service.model.Role;
import auth_service.auth_service.model.Users;
import auth_service.auth_service.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {
    private final UsersRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TelegramVerificationRepository telegramVerificationRepository;
    private final RefreshTokenService refreshTokenService;

    private final AuthMapper authMapper;
    @Transactional
    public AuthenticationResponseDto register(RegisterRequestDto registerRequestDto) {
        if(userRepository.findByEmail(registerRequestDto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("User with this email already exists.");
        }
        Users user = Users.builder()
                .email(registerRequestDto.getEmail())
                .password(passwordEncoder.encode(registerRequestDto.getPassword()))
                .username(registerRequestDto.getUsername())
                .role(Role.CLIENT)
                .verified(false)
                .build();
        Users savedUser = userRepository.save(user);
        String code = generateVerificationCode();

        TelegramVerification verification = TelegramVerification.builder()
                .user(savedUser)
                .code(code)
                .used(false)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        telegramVerificationRepository.save(verification);
        log.info("User {} registered. Verification code: {}", savedUser.getEmail(), code);
        String jwtAccessToken = jwtService.generateToken(user, user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());

        return authMapper.toAuthResponse(savedUser, jwtAccessToken, refreshToken.getToken(),code);
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

        return authMapper.toAuthResponse(user, jwtAccessToken, refreshToken.getToken(),null);
    }
    @Transactional
    public AuthenticationResponseDto refreshToken(RefreshTokenRequestDto request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Refresh token is not in database!"));

        refreshTokenService.verifyExpiration(refreshToken);

        Users user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user, user.getId());

        return authMapper.toAuthResponse(user, newAccessToken, refreshToken.getToken(),null);
    }
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.findByToken(refreshToken)
                .ifPresent(refreshTokenService::delete);
    }
    @Transactional
    public Long  verifyByCode(String code) {
        TelegramVerification verification = telegramVerificationRepository
                .findByCodeAndUsedFalse(code)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification code."));

        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Verification code has expired.");
        }

        verification.setUsed(true);
        verification.getUser().setVerified(true);

        telegramVerificationRepository.save(verification);
        userRepository.save(verification.getUser());

        log.info("User {} verified successfully", verification.getUser().getEmail());
        return verification.getUser().getId();
    }

    private String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }



}
