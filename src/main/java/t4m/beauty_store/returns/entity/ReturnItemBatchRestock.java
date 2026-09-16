package t4m.beauty_store.returns.entity;

import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.product.entity.InventoryBatch;

import java.time.LocalDateTime;

@Entity
@Table(name = "return_item_batch_restocks", uniqueConstraints =
    @UniqueConstraint(name = "uk_return_item_batch_restock", columnNames = {"return_item_id", "inventory_batch_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnItemBatchRestock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_item_id", nullable = false)
    private ReturnItem returnItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
