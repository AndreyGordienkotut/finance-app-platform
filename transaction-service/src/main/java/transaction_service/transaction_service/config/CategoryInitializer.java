package transaction_service.transaction_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.model.TransactionCategory;
import transaction_service.transaction_service.repository.TransactionCategoryRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional
public class CategoryInitializer implements CommandLineRunner {
    private final TransactionCategoryRepository repository;

    @Override
    public void run(String... args) {
        List<String> defaultNames = List.of("TRANSFER","FOOD", "RENT", "TRANSPORT", "SHOPPING", "OTHER", "ENTERTAINMENT");

        for (String name : defaultNames) {
            if (!repository.existsByNameAndUserIdIsNull(name)) {
                repository.save(TransactionCategory.builder()
                        .name(name)
                        .userId(null)
                        .build());
            }
        }
    }
}