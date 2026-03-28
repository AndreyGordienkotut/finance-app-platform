package transaction_service.transaction_service.config;

import core.core.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import transaction_service.transaction_service.dto.ClaudeRequest;
import transaction_service.transaction_service.dto.ClaudeResponse;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClaudeClient {

    @Value("${claude.api.key}")
    private String apiKey;

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-3-haiku-20240307";

    private final WebClient webClient = WebClient.create();

    public String analyze(String prompt) {
        ClaudeRequest request = ClaudeRequest.builder()
                .model(MODEL)
                .maxTokens(1024)
                .messages(List.of(
                        new ClaudeRequest.Message("user", prompt)
                ))
                .build();

        try {
            ClaudeResponse response = webClient.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, res -> {
                        log.warn("Claude rate limit exceeded");
                        return Mono.error(new ExternalServiceException("AI rate limit. Try later."));
                    })
                    .onStatus(status -> status.value() == 400, res ->
                            res.bodyToMono(String.class).flatMap(body -> {
                                log.error("Claude 400 error body: {}", body);
                                return Mono.error(new ExternalServiceException("Bad request: " + body));
                            })
                    )
                    .bodyToMono(ClaudeResponse.class)
                    .block();

            return response.getContent().get(0).getText();

        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API error: {}", e.getMessage());
            throw new ExternalServiceException("AI analysis unavailable");
        }
    }
}