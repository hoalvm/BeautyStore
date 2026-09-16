package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.admin.dto.ProductVariantRequest;
import t4m.beauty_store.product.dto.ProductVariantResponse;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.service.ProductVariantService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminVariantController {
    private final ProductVariantService variantService;

    @GetMapping("/products/{productId}/variants")
    public List<ProductVariantResponse> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return variantService.getVariants(productId, includeInactive).stream()
            .map(variant -> ProductVariantResponse.fromEntity(variant, null)).toList();
    }

    @PostMapping("/products/{productId}/variants")
    public ProductVariantResponse create(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVariantRequest request) {
        return ProductVariantResponse.fromEntity(variantService.create(productId, request), null);
    }

    @GetMapping("/variants/{id}")
    public ProductVariantResponse get(@PathVariable Long id) {
        return ProductVariantResponse.fromEntity(variantService.getVariant(id), null);
    }

    @PutMapping("/variants/{id}")
    public ProductVariantResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductVariantRequest request) {
        return ProductVariantResponse.fromEntity(variantService.update(id, request), null);
    }

    @DeleteMapping("/variants/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        variantService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/variants/{id}/visibility")
    public ProductVariantResponse setVisibility(@PathVariable Long id, @RequestParam boolean active) {
        return ProductVariantResponse.fromEntity(variantService.setActive(id, active), null);
    }
}
