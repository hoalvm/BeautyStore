package t4m.beauty_store.admin.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String slug;

    private Long brandId;

    private String material;
    private String origin;
    private String benefits;
    private String inci;
    private String directions;
    private String warnings;
    private String spf;

    @Min(value = 0, message = "PAO months must be at least 0")
    private Integer paoMonths;

    @Min(value = 0, message = "Shelf life months must be at least 0")
    private Integer shelfLifeMonths;

    private Set<Long> facetIds;
    private ProductVariantRequest defaultVariant;

    @Min(value = 0, message = "Warranty months must be at least 0")
    private Integer warrantyMonths;

    private String specifications;
    private String description;

    private Long categoryId;
    private Boolean featured;
    private Boolean active;
}
