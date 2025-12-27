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
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String authHeader = attributes.getRequest().getHeader("Authorization");
            if (authHeader != null) {
                template.header("Authorization", authHeader);
                log.info("Token attached to Feign request for URL: {}", template.url());
            } else {
                log.warn("No Authorization header found in current request!");
            }
        } else {
            log.warn("No RequestContextAttributes found!");
        }
    }
}