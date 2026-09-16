package t4m.beauty_store.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.auth.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cart")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "guest_token_hash", unique = true, length = 64)
    private String guestTokenHash;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();

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

    // Helper method to calculate total
    public BigDecimal getTotalPrice() {
        return cartItems.stream()
                .filter(CartItem::isAvailable)
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Helper method to add item to cart
    public void addItem(CartItem item) {
        cartItems.add(item);
        item.setCart(this);
    }

    // Helper method to remove item from cart
    public void removeItem(CartItem item) {
        cartItems.remove(item);
        item.setCart(null);
    }
}
