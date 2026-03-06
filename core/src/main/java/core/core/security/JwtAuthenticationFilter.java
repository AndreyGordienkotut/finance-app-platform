package core.core.security;

import core.core.config.JwtClaims;
import core.core.dto.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        log.info("Checking token for request: {}", request.getRequestURI());

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        if (jwtService.isTokenValid(token)) {
            Claims claims = jwtService.extractAllClaims(token);

            String email = claims.getSubject();
            Long userId = claims.get(JwtClaims.USER_ID, Long.class);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get(JwtClaims.ROLES, List.class);

            if (email != null && userId != null) {
                List<SimpleGrantedAuthority> authorities = roles == null ? List.of() :
                        roles.stream().map(SimpleGrantedAuthority::new).toList();

                AuthenticatedUser principal = new AuthenticatedUser(userId, email, authorities);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}

