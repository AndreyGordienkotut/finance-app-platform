package transaction_service.transaction_service.config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;
//@TestConfiguration
//public class TestSecurityConfig {
//
//    @Bean
//    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .authorizeHttpRequests(authorize -> authorize
//                        .anyRequest().permitAll()
//                )
//
//                .httpBasic(AbstractHttpConfigurer::disable);
//
//        return http.build();
//    }
//}