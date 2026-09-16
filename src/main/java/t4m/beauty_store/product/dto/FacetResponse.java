package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.ProductFacet;
import t4m.beauty_store.product.entity.ProductFacetType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetResponse {
    private Long id;
    private ProductFacetType type;
    private String code;
    private String label;

    public static FacetResponse fromEntity(ProductFacet facet) {
        return FacetResponse.builder()
            .id(facet.getId())
            .type(facet.getType())
            .code(facet.getCode())
            .label(facet.getLabel())
            .build();
    }
}
