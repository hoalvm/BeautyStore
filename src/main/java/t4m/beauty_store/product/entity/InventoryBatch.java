package t4m.beauty_store.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.config.StoreTime;

import java.time.LocalDate;

@Entity
@Table(
    name = "inventory_batches",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_batches_variant_code",
        columnNames = {"variant_id", "batch_code"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    @JsonIgnore
    private ProductVariant variant;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "manufactured_date")
    private LocalDate manufacturedDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Builder.Default
    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand = 0;

    @Builder.Default
    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Version
    private Long version;

    @Transient
    public int getAvailableQuantity() {
        return Math.max(0, safe(quantityOnHand) - safe(quantityReserved));
    }

    @Transient
    public boolean isSellableToday() {
        return Boolean.TRUE.equals(active)
            && expiryDate != null
            && !expiryDate.isBefore(StoreTime.today())
            && getAvailableQuantity() > 0;
    }

    @PrePersist
    @PreUpdate
    void validateAndNormalize() {
        if (batchCode != null) {
            batchCode = batchCode.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (quantityOnHand == null) quantityOnHand = 0;
        if (quantityReserved == null) quantityReserved = 0;
        if (active == null) active = true;
        if (quantityOnHand < 0 || quantityReserved < 0 || quantityReserved > quantityOnHand) {
            throw new IllegalStateException("Invalid inventory batch quantities");
        }
        if (manufacturedDate != null && expiryDate != null && manufacturedDate.isAfter(expiryDate)) {
            throw new IllegalStateException("Manufactured date must not be after expiry date");
        }
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
