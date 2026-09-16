package t4m.beauty_store.product.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import t4m.beauty_store.product.dto.*;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.service.ProductService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(productService.getAllProducts(PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        Product product = productService.getActiveProductById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return ResponseEntity.ok(ProductResponse.fromEntity(product));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductBySlug(@PathVariable String slug) {
        Product product = productService.getActiveProductBySlug(slug);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return ResponseEntity.ok(ProductResponse.fromEntity(product));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<ProductResponse>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts().stream()
            .map(ProductResponse::fromEntity).toList());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        validatePage(page, size);
        return ResponseEntity.ok(productService.searchProducts(keyword, PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<ProductResponse>> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String brandSlug,
            @RequestParam(required = false) String skinType,
            @RequestParam(required = false) String concern,
            @RequestParam(required = false) String hairType,
            @RequestParam(required = false) String hairConcern,
            @RequestParam(required = false) String form,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String ingredient,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        validatePage(page, size);
        String selectedBrand = brandSlug == null || brandSlug.isBlank() ? brand : brandSlug;
        ProductService.ProductFilter filter = new ProductService.ProductFilter(
            keyword, categoryId, category, selectedBrand, skinType, concern, hairType, hairConcern,
            form, finish, ingredient, minPrice, maxPrice, inStock, onSale, sort);
        return ResponseEntity.ok(productService.filterProducts(filter, PageRequest.of(page, size))
            .map(ProductResponse::fromEntity));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(CategoryResponse.treeFromEntities(productService.getAllCategories()));
    }

    @GetMapping("/brands")
    public ResponseEntity<List<BrandResponse>> getAllBrands() {
        return ResponseEntity.ok(productService.getAllBrands().stream().map(BrandResponse::fromEntity).toList());
    }

    @GetMapping("/facets")
    public ResponseEntity<List<FacetResponse>> getAllFacets() {
        return ResponseEntity.ok(productService.getAllFacets().stream()
            .map(FacetResponse::fromEntity).toList());
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Số trang phải từ 0 trở lên");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Kích thước trang phải từ 1 đến 100");
    }
}
