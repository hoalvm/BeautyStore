package t4m.beauty_store.image.entity;

import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_evidence_uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingEvidenceUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_kind", nullable = false, length = 20)
    private EvidenceKind evidenceKind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "cloudinary_public_id", nullable = false, unique = true, length = 255)
    private String cloudinaryPublicId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
