package t4m.beauty_store.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "product_variants",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_variants_sku", columnNames = "sku")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(unique = true, length = 100)
    private String barcode;

    @Column(length = 160)
    private String label;

    @Column(name = "shade_name", length = 120)
    private String shadeName;

    @Column(name = "shade_hex", length = 7)
    private String shadeHex;

    @Column(name = "size_value", precision = 12, scale = 3)
    private BigDecimal sizeValue;

    @Column(name = "size_unit", length = 30)
    private String sizeUnit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_price", precision = 15, scale = 2)
    private BigDecimal discountPrice;

    @Builder.Default
    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 10;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean defaultVariant = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("expiryDate ASC, id ASC")
    private List<InventoryBatch> batches = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "variant")
    @JsonIgnore
    private List<ProductImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        validateAndNormalize();
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        validateAndNormalize();
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public int getAvailableStock() {
        return batches == null ? 0 : batches.stream()
            .filter(InventoryBatch::isSellableToday)
            .mapToInt(InventoryBatch::getAvailableQuantity)
            .sum();
    }

    @Transient
    public BigDecimal getEffectivePrice() {
        return discountPrice != null ? discountPrice : price;
    }

    private void validateAndNormalize() {
        if (sku != null) {
            sku = sku.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (shadeHex != null) {
            shadeHex = shadeHex.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (active == null) active = true;
        if (defaultVariant == null) defaultVariant = false;
        if (lowStockThreshold == null) lowStockThreshold = 10;
    }
}
