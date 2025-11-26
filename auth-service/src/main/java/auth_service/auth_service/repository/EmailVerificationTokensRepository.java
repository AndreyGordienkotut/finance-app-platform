package auth_service.auth_service.repository;

import auth_service.auth_service.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface EmailVerificationTokensRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByToken(String token);

}