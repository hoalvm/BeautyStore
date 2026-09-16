package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.ProductVariant;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
    private Long id;
    private String sku;
    private String barcode;
    private String label;
    private String shadeName;
    private String shadeHex;
    private BigDecimal sizeValue;
    private String sizeUnit;
    private BigDecimal volume;
    private String volumeUnit;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Integer lowStockThreshold;
    private Boolean defaultVariant;
    private Boolean active;
    private Integer availableStock;
    private String imageUrl;

    public static ProductVariantResponse fromEntity(ProductVariant variant, String imageUrl) {
        return ProductVariantResponse.builder()
            .id(variant.getId())
            .sku(variant.getSku())
            .barcode(variant.getBarcode())
            .label(variant.getLabel())
            .shadeName(variant.getShadeName())
            .shadeHex(variant.getShadeHex())
            .sizeValue(variant.getSizeValue())
            .sizeUnit(variant.getSizeUnit())
            .volume(variant.getSizeValue())
            .volumeUnit(variant.getSizeUnit())
            .price(variant.getPrice())
            .discountPrice(variant.getDiscountPrice())
            .lowStockThreshold(variant.getLowStockThreshold())
            .defaultVariant(Boolean.TRUE.equals(variant.getDefaultVariant()))
            .active(Boolean.TRUE.equals(variant.getActive()))
            .availableStock(variant.getAvailableStock())
            .imageUrl(imageUrl)
            .build();
    }
}
