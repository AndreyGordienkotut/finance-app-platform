package transaction_service.transaction_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Page<Transaction> findBySourceAccountIdOrTargetAccountId(
            Long sourceAccountId, Long targetAccountId, Pageable pageable);
//    List<Transaction> findByStatusAndCreatedAtBefore(Status status, LocalDateTime dateTime);

    List<Transaction> findByStatusAndUpdatedAtBefore(Status status, LocalDateTime dateTime);
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.userId = :userId " +
            "AND t.createdAt > :since " +
            "AND t.status = 'SUCCESS' " +
            "AND t.typeTransaction IN ('TRANSFER', 'WITHDRAW')")
    BigDecimal calculateTotalSpentForUserInLast24Hours(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );
}
