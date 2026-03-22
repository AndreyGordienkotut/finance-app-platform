package auth_service.auth_service.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "telegram_verification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id", nullable = false)
    private Users user;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;
}