package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.admin.dto.CategoryRequest;
import t4m.beauty_store.product.dto.CategoryResponse;
import t4m.beauty_store.product.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminCategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.getCategories(includeInactive).stream().map(CategoryResponse::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return CategoryResponse.fromEntity(categoryService.getCategory(id));
    }

    @PostMapping
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.fromEntity(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.fromEntity(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        categoryService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/visibility")
    public CategoryResponse setVisibility(@PathVariable Long id, @RequestParam boolean active) {
        return CategoryResponse.fromEntity(categoryService.setActive(id, active));
    }
}
