package core.core.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public record AuthenticatedUser(
        Long userId,
        String email,
        Collection<? extends GrantedAuthority> authorities
) {
}