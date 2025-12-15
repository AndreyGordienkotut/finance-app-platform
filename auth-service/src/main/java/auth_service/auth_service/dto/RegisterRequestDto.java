package auth_service.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDto {
    @Email(message = "Email should be valid")
    @NotBlank
    private String email;
    @Size(min = 6, message = "Password must be at least 6 characters long")
    @NotBlank
    private String password;
    @NotBlank(message = "Username name cannot be empty")
    private String username;


}
