package account_service.account_service.repository;

import account_service.account_service.model.Account;
import core.core.enums.StatusAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Page<Account> findAllByUserIdAndStatusAccountNotOrderByCreateAtAsc(Long userId, StatusAccount statusAccount, Pageable pageable);

    long countByUserIdAndStatusAccount(Long userId, StatusAccount statusAccount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}