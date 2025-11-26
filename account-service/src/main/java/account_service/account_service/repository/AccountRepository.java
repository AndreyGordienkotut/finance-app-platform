package account_service.account_service.repository;

import account_service.account_service.model.Account;
import account_service.account_service.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Page<Account> findAllByUserIdAndStatusNotOrderByCreateAtAsc(Long userId, Status status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, Status status);


}