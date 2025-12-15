package core.core.exception;

public class ConflictException extends RuntimeException {
    private final Object payload;


    public ConflictException(String message, Object payload) {
        super(message);
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}