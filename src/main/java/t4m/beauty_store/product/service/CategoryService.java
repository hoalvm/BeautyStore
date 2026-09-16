package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.CategoryRequest;
import t4m.beauty_store.product.entity.Category;
import t4m.beauty_store.product.repository.CategoryRepository;
import t4m.beauty_store.product.util.Slugifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public List<Category> getCategories(boolean includeInactive) {
        return includeInactive ? categoryRepository.findAll() : categoryRepository.findByActiveTrue();
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

    @Transactional
    public Category create(CategoryRequest request) {
        String name = BrandService.required(request.getName(), "Category name is required");
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("Category name already exists");
        }
        return categoryRepository.save(Category.builder()
            .name(name)
            .slug(uniqueSlug(request.getSlug(), name, null))
            .description(BrandService.clean(request.getDescription()))
            .icon(BrandService.clean(request.getIcon()))
            .parent(resolveParent(request.getParentId(), null))
            .displayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder())
            .active(request.getActive() == null || request.getActive())
            .build());
    }

    @Transactional
    public Category update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        String name = BrandService.required(request.getName(), "Category name is required");
        categoryRepository.findByNameIgnoreCase(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new IllegalArgumentException("Category name already exists"); });
        category.setName(name);
        category.setSlug(uniqueSlug(request.getSlug(), name, id));
        category.setDescription(BrandService.clean(request.getDescription()));
        category.setIcon(BrandService.clean(request.getIcon()));
        category.setParent(resolveParent(request.getParentId(), id));
        category.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        if (request.getActive() != null) category.setActive(request.getActive());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category setActive(Long id, boolean active) {
        Category category = getCategory(id);
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private Category resolveParent(Long parentId, Long categoryId) {
        if (parentId == null) return null;
        if (parentId.equals(categoryId)) {
            throw new IllegalArgumentException("A category cannot be its own parent");
        }
        Category parent = getCategory(parentId);
        Set<Long> visited = new HashSet<>();
        Category cursor = parent;
        while (cursor != null) {
            if (!visited.add(cursor.getId())) {
                throw new IllegalArgumentException("Category hierarchy contains a cycle");
            }
            if (cursor.getId().equals(categoryId)) {
                throw new IllegalArgumentException("Category parent would create a cycle");
            }
            cursor = cursor.getParent();
        }
        return parent;
    }

    private String uniqueSlug(String requested, String name, Long currentId) {
        String base = Slugifier.slugify(BrandService.clean(requested) == null ? name : requested);
        String candidate = base;
        int suffix = 2;
        while (currentId == null
            ? categoryRepository.existsBySlugIgnoreCase(candidate)
            : categoryRepository.existsBySlugIgnoreCaseAndIdNot(candidate, currentId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
