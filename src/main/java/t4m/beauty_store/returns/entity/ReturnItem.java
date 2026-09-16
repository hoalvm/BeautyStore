package t4m.beauty_store.returns.entity;

import jakarta.persistence.*;
import lombok.*;
import t4m.beauty_store.order.entity.OrderItem;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "return_items", uniqueConstraints = {
    @UniqueConstraint(name = "uk_return_item_request_order_item", columnNames = {"return_request_id", "order_item_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReturnReason reason;

    @Column(name = "unopened_and_sealed", nullable = false)
    private Boolean unopenedAndSealed;

    @Column(columnDefinition = "TEXT")
    private String details;

    @ElementCollection
    @CollectionTable(name = "return_item_images", joinColumns = @JoinColumn(name = "return_item_id"))
    @Column(name = "image_url", nullable = false, length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false)
    private Boolean restocked = false;

    @Column(name = "restocked_at")
    private LocalDateTime restockedAt;

    @OneToMany(mappedBy = "returnItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ReturnItemBatchRestock> batchRestocks = new ArrayList<>();

    public void addBatchRestock(ReturnItemBatchRestock restock) {
        batchRestocks.add(restock);
        restock.setReturnItem(this);
    }
}
