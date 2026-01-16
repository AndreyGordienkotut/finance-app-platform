package auth_service.auth_service.controller;

import auth_service.auth_service.dto.AuthenticationRequestDto;
import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.dto.RefreshTokenRequestDto;
import auth_service.auth_service.dto.RegisterRequestDto;
import auth_service.auth_service.service.AuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthorizationController {
    private final AuthorizationService userService;
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return new ResponseEntity<>(userService.register(request), HttpStatus.OK);
    }
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDto> authenticate(@Valid @RequestBody AuthenticationRequestDto request) {
        return new ResponseEntity<>(userService.authenticate(request), HttpStatus.OK);
    }
    @PostMapping("/refreshToken")
    public ResponseEntity<AuthenticationResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        return new ResponseEntity<>(userService.refreshToken(request), HttpStatus.OK);
    }
    @PostMapping("/logout")
    public ResponseEntity<AuthenticationResponseDto> logout(@RequestBody RefreshTokenRequestDto request) {
        userService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
