package auth_service.auth_service.repository;


import auth_service.auth_service.model.TelegramVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface TelegramVerificationRepository extends JpaRepository<TelegramVerification, Long> {
    Optional<TelegramVerification> findByCodeAndUsedFalse(String code);

}