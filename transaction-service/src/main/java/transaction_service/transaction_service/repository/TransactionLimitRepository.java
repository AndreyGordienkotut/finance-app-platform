package transaction_service.transaction_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import transaction_service.transaction_service.model.TransactionLimit;

import java.util.Optional;


@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long> {
    Optional<TransactionLimit> findByUserId(Long userId);

}