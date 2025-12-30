package transaction_service.transaction_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import transaction_service.transaction_service.model.TransactionCategory;

import java.util.List;
import java.util.Optional;


@Repository
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, Long> {
    List<TransactionCategory> findByUserIdOrUserIdIsNull(Long userId);
    Optional<TransactionCategory> findByNameAndUserId(String name, Long userId);
    Boolean existsByNameAndUserIdIsNull(String name);
}