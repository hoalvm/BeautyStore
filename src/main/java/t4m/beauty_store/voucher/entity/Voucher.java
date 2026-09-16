package t4m.beauty_store.voucher.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.product.entity.Brand;
import t4m.beauty_store.product.entity.Category;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "voucher")
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    // Giá trị giảm (% hoặc tiền cố định)
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    // Giảm tối đa (cho loại PERCENTAGE)
    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    // Giá trị đơn hàng tối thiểu
    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;

    // Tổng số lượng phát hành
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    // Số lượng đã sử dụng
    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity = 0;

    // Giới hạn mỗi người dùng
    @Column(name = "limit_per_user")
    private Integer limitPerUser;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // Áp dụng cho nhóm người dùng (comma-separated roles)
    @Column(name = "applicable_user_groups", columnDefinition = "TEXT")
    private String applicableUserGroups;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "voucher_brands",
        joinColumns = @JoinColumn(name = "voucher_id"),
        inverseJoinColumns = @JoinColumn(name = "brand_id"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Brand> brands = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "voucher_categories",
        joinColumns = @JoinColumn(name = "voucher_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "voucher_products",
        joinColumns = @JoinColumn(name = "voucher_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Product> products = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "voucher_variants",
        joinColumns = @JoinColumn(name = "voucher_id"),
        inverseJoinColumns = @JoinColumn(name = "variant_id"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<ProductVariant> variants = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = StoreTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = StoreTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = StoreTime.now();
    }

    // Helper method to check if voucher is currently valid
    public boolean isCurrentlyValid() {
        LocalDateTime now = StoreTime.now();
        return Boolean.TRUE.equals(active)
            && startDate != null && !now.isBefore(startDate)
            && endDate != null && !now.isAfter(endDate)
            && hasAvailableQuantity();
    }

    // Helper method to check if voucher has available quantity
    public boolean hasAvailableQuantity() {
        return usedQuantity != null && totalQuantity != null && usedQuantity < totalQuantity;
    }
}
