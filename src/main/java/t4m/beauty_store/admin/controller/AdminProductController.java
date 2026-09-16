package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import t4m.beauty_store.admin.dto.*;
import t4m.beauty_store.product.dto.ProductResponse;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.service.ProductService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProductController {
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Product> products = search == null || search.isBlank()
            ? productService.getAllProducts(pageable, includeInactive)
            : productService.searchProducts(search, pageable, includeInactive);
        return ResponseEntity.ok(products.map(ProductResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return ResponseEntity.ok(ProductResponse.fromEntity(product));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(ProductResponse.fromEntity(productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        Product product = productService.updateProduct(id, request);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return ResponseEntity.ok(ProductResponse.fromEntity(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> archive(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("message", "Đã ẩn sản phẩm"));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<ProductResponse> setVisibility(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ProductResponse.fromEntity(productService.setProductActive(id, active)));
    }

    @GetMapping("/stats/stock")
    public ResponseEntity<ProductStockStats> getStockStats() {
        return ResponseEntity.ok(productService.getStockStats());
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<Page<ProductResponse>> getOutOfStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getOutOfStockProducts(PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<Page<ProductResponse>> getLowStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "10") int threshold) {
        return ResponseEntity.ok(productService.getLowStockProducts(threshold, PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/in-stock")
    public ResponseEntity<Page<ProductResponse>> getInStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int threshold) {
        return ResponseEntity.ok(productService.getInStockProducts(threshold, PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }
}
