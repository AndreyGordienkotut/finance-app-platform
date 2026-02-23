package transaction_service.transaction_service.event;


public class TransactionCompletedEvent {
    private final Long userId;

    public TransactionCompletedEvent(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}