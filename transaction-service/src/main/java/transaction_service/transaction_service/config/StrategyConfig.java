package transaction_service.transaction_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import transaction_service.transaction_service.model.TransactionType;
import transaction_service.transaction_service.service.strategy.FinancialOperationStrategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class StrategyConfig {

    @Bean
    public Map<TransactionType, FinancialOperationStrategy> strategyMap(
            List<FinancialOperationStrategy> strategies) {
        return strategies.stream()
                .collect(Collectors.toMap(
                        FinancialOperationStrategy::getType,
                        s -> s
                ));
    }
}