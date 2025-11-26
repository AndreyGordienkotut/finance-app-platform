package auth_service.auth_service.service;

import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.exception.BadRequestException;
import auth_service.auth_service.exception.InvalidCredentialsException;
import auth_service.auth_service.exception.UserAlreadyExistsException;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        user.setUsername(registerRequestDto.getEmail());
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
        Map<String, Object> extra = new HashMap<>();
        extra.put("userId", user.getId());
        String jwtAccessToken = jwtService.generateToken(extra, user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());
        return AuthenticationResponseDto.builder()
                .token(jwtAccessToken)
                .refreshToken(refreshToken.getToken())
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .build();
    }
    @Transactional
    public AuthenticationResponseDto authenticate(AuthenticationRequestDto authenticationRequestDto) {
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
        Users user = userRepository.findByEmail(authenticationRequestDto.getEmail()).orElseThrow(()-> new UsernameNotFoundException("User not found with email: " +authenticationRequestDto.getEmail()));
        if (!user.isVerified()) {
            throw new BadRequestException("Email is not verified. Please check your inbox.");
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("userId", user.getId());
        String jwtAccessToken = jwtService.generateToken(extra, user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        return AuthenticationResponseDto.builder()
                .token(jwtAccessToken)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
    @Transactional
    public AuthenticationResponseDto refreshToken(RefreshTokenRequestDto request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Refresh token is not in database!"));

        refreshTokenService.verifyExpiration(refreshToken);

        Users user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);

        return AuthenticationResponseDto.builder()
                .token(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
    @Transactional
    public void logout(String refreshToken) {
        Optional<RefreshToken> tokenOptional = refreshTokenService.findByToken(refreshToken);
        if (tokenOptional.isPresent()) {
            refreshTokenService.delete(tokenOptional.get());
        }
    }



}
