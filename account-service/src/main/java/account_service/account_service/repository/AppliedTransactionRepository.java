package account_service.account_service.repository;

import account_service.account_service.model.AppliedTransactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface AppliedTransactionRepository extends JpaRepository<AppliedTransactions, Long> {
    Boolean existsByTransactionIdAndAccountId(Long transactionId, Long accountId);
}
