package t4m.beauty_store.voucher.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.order.entity.Order;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "voucher_usage")
public class VoucherUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_identifier_hash", length = 64)
    private String guestIdentifierHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt = StoreTime.now();
}
