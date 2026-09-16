package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.ProductImage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponse {
    private Long id;
    private String url;
    private String altText;
    private Integer sortOrder;
    private Boolean primary;
    private Long variantId;

    public static ProductImageResponse fromEntity(ProductImage image) {
        return ProductImageResponse.builder()
            .id(image.getId())
            .url(image.getUrl())
            .altText(image.getAltText())
            .sortOrder(image.getSortOrder())
            .primary(Boolean.TRUE.equals(image.getPrimary()))
            .variantId(image.getVariant() == null ? null : image.getVariant().getId())
            .build();
    }
}
