package transaction_service.transaction_service.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import transaction_service.transaction_service.service.AnalyticsCacheEvictService;

@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final AnalyticsCacheEvictService cacheEvictService;

    @EventListener
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        cacheEvictService.evictUserAnalytics(event.getUserId());
    }
}