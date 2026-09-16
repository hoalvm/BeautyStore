package t4m.beauty_store.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {
    private Long id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderValue;
    private Integer totalQuantity;
    private Integer usedQuantity;
    private Integer limitPerUser;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean active;
    private Set<Long> brandIds;
    private Set<Long> categoryIds;
    private Set<Long> productIds;
    private Set<Long> variantIds;
    private String applicableUserGroups;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status; // "ACTIVE", "UPCOMING", "EXPIRED", "DISABLED"

    public static VoucherResponse fromEntity(Voucher voucher) {
        VoucherResponse response = new VoucherResponse();
        response.setId(voucher.getId());
        response.setCode(voucher.getCode());
        response.setDescription(voucher.getDescription());
        response.setDiscountType(voucher.getDiscountType());
        response.setDiscountValue(voucher.getDiscountValue());
        response.setMaxDiscount(voucher.getMaxDiscount());
        response.setMinOrderValue(voucher.getMinOrderValue());
        response.setTotalQuantity(voucher.getTotalQuantity());
        response.setUsedQuantity(voucher.getUsedQuantity());
        response.setLimitPerUser(voucher.getLimitPerUser());
        response.setStartDate(voucher.getStartDate());
        response.setEndDate(voucher.getEndDate());
        response.setActive(voucher.getActive());
        response.setBrandIds(voucher.getBrands().stream().map(brand -> brand.getId()).collect(Collectors.toSet()));
        response.setCategoryIds(voucher.getCategories().stream().map(category -> category.getId()).collect(Collectors.toSet()));
        response.setProductIds(voucher.getProducts().stream().map(product -> product.getId()).collect(Collectors.toSet()));
        response.setVariantIds(voucher.getVariants().stream().map(variant -> variant.getId()).collect(Collectors.toSet()));
        response.setApplicableUserGroups(voucher.getApplicableUserGroups());
        response.setCreatedAt(voucher.getCreatedAt());
        response.setUpdatedAt(voucher.getUpdatedAt());
        
        // Calculate status
        LocalDateTime now = StoreTime.now();
        if (!voucher.getActive()) {
            response.setStatus("DISABLED");
        } else if (now.isBefore(voucher.getStartDate())) {
            response.setStatus("UPCOMING");
        } else if (now.isAfter(voucher.getEndDate())) {
            response.setStatus("EXPIRED");
        } else if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            response.setStatus("OUT_OF_STOCK");
        } else {
            response.setStatus("ACTIVE");
        }
        
        return response;
    }
}
