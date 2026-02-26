package core.core.exception;

public class ConflictException extends RuntimeException {
    private final Object payload;



    public ConflictException(String message) {
        super(message);
        this.payload = null;
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.payload = null;
    }

    public ConflictException(String message, Throwable cause, Object payload) {
        super(message, cause);
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}