package transaction_service.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeResponse {
    private List<ContentBlock> content;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlock {
        private String type;
        private String text;
    }
}