package transaction_service.transaction_service.event.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import transaction_service.transaction_service.model.Transaction;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, core.core.dto.TransactionKafkaEvent> kafkaTemplate;
    private static final String TOPIC = "transaction.completed";

    public void publish(Transaction tx) {
        core.core.dto.TransactionKafkaEvent event = core.core.dto.TransactionKafkaEvent.builder()
                .transactionId(tx.getId())
                .userId(tx.getUserId())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .amount(tx.getAmount())
                .targetAmount(tx.getTargetAmount())
                .exchangeRate(tx.getExchangeRate())
                .currency(tx.getCurrency().name())
                .transactionType(tx.getTransactionType().name())
                .categoryName(tx.getCategory() != null ? tx.getCategory().getName() : null)
                .createdAt(tx.getCreatedAt())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(tx.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish Kafka event for TX {}: {}",
                                tx.getId(), ex.getMessage());
                    } else {
                        log.info("Published Kafka event for TX {} to topic {}",
                                tx.getId(), TOPIC);
                    }
                });
    }
}