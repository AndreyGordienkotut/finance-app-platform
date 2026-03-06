package transaction_service.transaction_service.config;

import core.core.security.JwtService;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends core.core.security.JwtAuthenticationFilter {

    public JwtAuthenticationFilter(JwtService jwtService) {
        super(jwtService);
    }
}
