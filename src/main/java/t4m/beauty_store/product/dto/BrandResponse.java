package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.Brand;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String logoUrl;
    private String country;
    private Boolean active;

    public static BrandResponse fromEntity(Brand brand) {
        if (brand == null) return null;
        return BrandResponse.builder()
            .id(brand.getId())
            .name(brand.getName())
            .slug(brand.getSlug())
            .description(brand.getDescription())
            .logoUrl(brand.getLogoUrl())
            .country(brand.getCountry())
            .active(Boolean.TRUE.equals(brand.getActive()))
            .build();
    }
}
