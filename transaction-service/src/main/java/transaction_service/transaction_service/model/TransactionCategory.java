package transaction_service.transaction_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "transaction_category",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"userId", "name"})
        }
)
public class TransactionCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name ="user_id")
    private Long userId;
    @Column(nullable = false)
    private String name;
}
