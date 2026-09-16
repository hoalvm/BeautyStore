package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.admin.dto.BrandRequest;
import t4m.beauty_store.product.dto.BrandResponse;
import t4m.beauty_store.product.service.BrandService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminBrandController {
    private final BrandService brandService;

    @GetMapping
    public List<BrandResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return brandService.getBrands(includeInactive).stream().map(BrandResponse::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public BrandResponse get(@PathVariable Long id) {
        return BrandResponse.fromEntity(brandService.getBrand(id));
    }

    @PostMapping
    public BrandResponse create(@Valid @RequestBody BrandRequest request) {
        return BrandResponse.fromEntity(brandService.create(request));
    }

    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return BrandResponse.fromEntity(brandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        brandService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/visibility")
    public BrandResponse setVisibility(@PathVariable Long id, @RequestParam boolean active) {
        return BrandResponse.fromEntity(brandService.setActive(id, active));
    }
}
