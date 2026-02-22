package auth_service.auth_service.mapper;

import auth_service.auth_service.dto.AuthenticationResponseDto;
import auth_service.auth_service.model.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "token", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    AuthenticationResponseDto toAuthResponse(Users user, String accessToken, String refreshToken);
}