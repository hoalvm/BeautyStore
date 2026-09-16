package t4m.beauty_store.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal price; // Price at the time of adding to cart

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method to calculate subtotal
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isAvailable() {
        if (product == null || variant == null
                || !Boolean.TRUE.equals(product.getActive())
                || !Boolean.TRUE.equals(variant.getActive())) return false;
        if (product.getBrandEntity() != null
                && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) return false;
        var category = product.getCategory();
        int depth = 0;
        while (category != null && depth++ < 32) {
            if (!Boolean.TRUE.equals(category.getActive())) return false;
            category = category.getParent();
        }
        return category == null;
    }
}
