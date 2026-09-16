package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private BrandResponse brand;
    private String brandName;
    private CategoryInfo category;
    @Builder.Default
    private List<CategoryInfo> categoryBreadcrumb = new ArrayList<>();
    private String description;
    private String benefits;
    private String inci;
    private String directions;
    private String warnings;
    private String origin;
    private String material;
    private Integer warrantyMonths;
    private String specifications;
    private String spf;
    private Integer paoMonths;
    private Integer shelfLifeMonths;
    private Boolean featured;
    private Boolean active;
    @Builder.Default
    private List<FacetResponse> facets = new ArrayList<>();
    @Builder.Default
    private Map<ProductFacetType, List<FacetResponse>> facetsByType = new EnumMap<>(ProductFacetType.class);
    @Builder.Default
    private List<ProductImageResponse> gallery = new ArrayList<>();
    @Builder.Default
    private List<ProductVariantResponse> variants = new ArrayList<>();
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer totalAvailableStock;
    private Boolean inStock;
    private Double averageRating;
    private Integer ratingCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String slug;
        private String icon;
        private Long parentId;
        private Boolean active;

        static CategoryInfo fromEntity(Category category) {
            if (category == null) return null;
            return CategoryInfo.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .active(Boolean.TRUE.equals(category.getActive()))
                .build();
        }
    }

    public static ProductResponse fromEntity(Product product) {
        List<ProductVariant> activeVariants = product.getVariants() == null
            ? new ArrayList<>()
            : product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
                .sorted(Comparator
                    .comparing((ProductVariant variant) -> !Boolean.TRUE.equals(variant.getDefaultVariant()))
                    .thenComparing(variant -> variant.getId() == null ? Long.MAX_VALUE : variant.getId()))
                .toList();

        Set<Long> activeVariantIds = activeVariants.stream()
            .map(ProductVariant::getId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        List<ProductImage> images = product.getImages() == null
            ? List.of()
            : product.getImages().stream()
                .filter(image -> image.getVariant() == null
                    || (image.getVariant().getId() != null
                        && activeVariantIds.contains(image.getVariant().getId())))
                .sorted(Comparator
                    .comparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(image -> image.getId() == null ? Long.MAX_VALUE : image.getId()))
                .toList();

        ProductVariant defaultVariant = activeVariants.stream()
            .filter(variant -> Boolean.TRUE.equals(variant.getDefaultVariant()))
            .findFirst()
            .orElse(activeVariants.isEmpty() ? null : activeVariants.getFirst());

        List<ProductVariantResponse> variantResponses = activeVariants.stream()
            .map(variant -> ProductVariantResponse.fromEntity(variant, primaryImage(images, variant)))
            .toList();
        List<ProductImageResponse> gallery = images.stream()
            .map(ProductImageResponse::fromEntity)
            .toList();

        BigDecimal minPrice = activeVariants.stream()
            .map(ProductVariant::getEffectivePrice)
            .filter(java.util.Objects::nonNull)
            .min(BigDecimal::compareTo)
            .orElse(null);
        BigDecimal maxPrice = activeVariants.stream()
            .map(ProductVariant::getEffectivePrice)
            .filter(java.util.Objects::nonNull)
            .max(BigDecimal::compareTo)
            .orElse(null);
        int availableStock = activeVariants.stream().mapToInt(ProductVariant::getAvailableStock).sum();

        String brandName = product.getBrandEntity() == null ? null : product.getBrandEntity().getName();
        List<FacetResponse> facetResponses = product.getFacets() == null ? List.of()
            : product.getFacets().stream()
                .filter(facet -> Boolean.TRUE.equals(facet.getActive()))
                .sorted(Comparator.comparing(ProductFacet::getType).thenComparing(ProductFacet::getLabel))
                .map(FacetResponse::fromEntity)
                .toList();
        Map<ProductFacetType, List<FacetResponse>> facetsByType = new EnumMap<>(ProductFacetType.class);
        facetResponses.forEach(facet -> facetsByType
            .computeIfAbsent(facet.getType(), ignored -> new ArrayList<>()).add(facet));

        return ProductResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .slug(product.getSlug())
            .brand(BrandResponse.fromEntity(product.getBrandEntity()))
            .brandName(brandName)
            .category(CategoryInfo.fromEntity(product.getCategory()))
            .categoryBreadcrumb(categoryBreadcrumb(product.getCategory()))
            .description(product.getDescription())
            .benefits(product.getBenefits())
            .inci(product.getInci())
            .directions(product.getDirections())
            .warnings(product.getWarnings())
            .origin(product.getOrigin())
            .material(product.getMaterial())
            .warrantyMonths(product.getWarrantyMonths())
            .specifications(product.getSpecifications())
            .spf(product.getSpf())
            .paoMonths(product.getPaoMonths())
            .shelfLifeMonths(product.getShelfLifeMonths())
            .featured(Boolean.TRUE.equals(product.getFeatured()))
            .active(Boolean.TRUE.equals(product.getActive()))
            .facets(facetResponses)
            .facetsByType(facetsByType)
            .gallery(gallery)
            .variants(variantResponses)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .totalAvailableStock(availableStock)
            .inStock(availableStock > 0)
            .averageRating(product.getAverageRating() == null ? 0.0 : product.getAverageRating())
            .ratingCount(product.getRatingCount() == null ? 0 : product.getRatingCount())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    private static String primaryImage(List<ProductImage> images, ProductVariant variant) {
        if (variant != null) {
            String variantImage = images.stream()
                .filter(image -> image.getVariant() != null && variant.getId() != null
                    && variant.getId().equals(image.getVariant().getId()))
                .sorted(Comparator
                    .comparing((ProductImage image) -> !Boolean.TRUE.equals(image.getPrimary()))
                    .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);
            if (variantImage != null) return variantImage;
        }
        return images.stream()
            .filter(image -> image.getVariant() == null)
            .sorted(Comparator
                .comparing((ProductImage image) -> !Boolean.TRUE.equals(image.getPrimary()))
                .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .map(ProductImage::getUrl)
            .findFirst()
            .orElse(null);
    }

    private static List<CategoryInfo> categoryBreadcrumb(Category category) {
        List<CategoryInfo> result = new ArrayList<>();
        Category cursor = category;
        int guard = 0;
        while (cursor != null && guard++ < 32) {
            result.addFirst(CategoryInfo.fromEntity(cursor));
            cursor = cursor.getParent();
        }
        return result;
    }

}
