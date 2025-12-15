package account_service.account_service.repository;

import account_service.account_service.model.Account;
import core.core.enums.StatusAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Page<Account> findAllByUserIdAndStatusAccountNotOrderByCreateAtAsc(Long userId, StatusAccount statusAccount, Pageable pageable);

    long countByUserIdAndStatusAccount(Long userId, StatusAccount statusAccount);


}