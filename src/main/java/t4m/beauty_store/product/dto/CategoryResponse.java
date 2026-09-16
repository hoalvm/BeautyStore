package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.Category;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String icon;
    private Long parentId;
    private Integer displayOrder;
    private Boolean active;
    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();

    public static CategoryResponse fromEntity(Category category) {
        return base(category).build();
    }

    public static List<CategoryResponse> treeFromEntities(Collection<Category> categories) {
        Map<Long, CategoryResponse> responses = new LinkedHashMap<>();
        categories.forEach(category -> responses.put(category.getId(), base(category).build()));

        List<CategoryResponse> roots = new ArrayList<>();
        for (Category category : categories) {
            CategoryResponse response = responses.get(category.getId());
            Long parentId = response.getParentId();
            if (parentId == null || !responses.containsKey(parentId)) {
                roots.add(response);
            } else {
                responses.get(parentId).getChildren().add(response);
            }
        }
        Comparator<CategoryResponse> order = Comparator
            .comparing(CategoryResponse::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(CategoryResponse::getName);
        responses.values().forEach(response -> response.getChildren().sort(order));
        roots.sort(order);
        return roots;
    }

    private static CategoryResponseBuilder base(Category category) {
        return CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .slug(category.getSlug())
            .description(category.getDescription())
            .icon(category.getIcon())
            .parentId(category.getParent() == null ? null : category.getParent().getId())
            .displayOrder(category.getDisplayOrder())
            .active(Boolean.TRUE.equals(category.getActive()));
    }
}
