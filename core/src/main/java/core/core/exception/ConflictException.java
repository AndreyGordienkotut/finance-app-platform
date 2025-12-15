package core.core.exception;

public class ConflictException extends RuntimeException {
    private final Object payload; // Для возврата DTO (например, TransactionResponseDto)

    public ConflictException(String message) {
        super(message);
        this.payload = null;
    }

    public ConflictException(String message, Object payload) {
        super(message);
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}