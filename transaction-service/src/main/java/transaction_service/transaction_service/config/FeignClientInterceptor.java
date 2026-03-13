package transaction_service.transaction_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
@Slf4j
@Component
public class FeignClientInterceptor implements RequestInterceptor {
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static void clearToken() {
        TOKEN_HOLDER.remove();
    }
    @Override
    public void apply(RequestTemplate template) {
        String token = TOKEN_HOLDER.get();

        if (token == null) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                token = attributes.getRequest().getHeader("Authorization");
            }
        }
        if (token != null) {
            template.header("Authorization", token);
        } else {
            log.warn("No auth token found for Feign request to {}", template.url());
        }
    }
}