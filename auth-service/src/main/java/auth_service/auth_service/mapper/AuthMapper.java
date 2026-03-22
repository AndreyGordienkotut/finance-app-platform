package auth_service.auth_service.mapper;

import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.model.Users;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    default AuthenticationResponseDto toAuthResponse(
            Users user,
            String accessToken,
            String refreshToken,
            String verificationCode) {

        return AuthenticationResponseDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .token(accessToken)
                .refreshToken(refreshToken)
                .verificationCode(verificationCode)
                .build();
    }
}