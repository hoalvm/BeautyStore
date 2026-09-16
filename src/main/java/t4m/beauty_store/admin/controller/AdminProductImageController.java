package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.admin.dto.ProductImageRequest;
import t4m.beauty_store.product.dto.ProductImageResponse;
import t4m.beauty_store.product.entity.ProductImage;
import t4m.beauty_store.product.service.CloudinaryService;
import t4m.beauty_store.product.service.ProductImageService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProductImageController {
    private final ProductImageService imageService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return imageService.getImages(productId).stream().map(ProductImageResponse::fromEntity).toList();
    }

    @PostMapping
    public ProductImageResponse create(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageRequest request) {
        return ProductImageResponse.fromEntity(imageService.create(productId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImageResponse upload(
            @PathVariable Long productId,
            @RequestParam("image") MultipartFile image,
            @RequestParam String altText,
            @RequestParam(required = false) Long variantId,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(defaultValue = "false") Boolean primary) throws Exception {
        Map<String, String> uploaded = cloudinaryService.uploadImage(image);
        String publicId = uploaded.get("publicId");
        try {
            ProductImageRequest request = new ProductImageRequest();
            request.setUrl(uploaded.get("url"));
            request.setCloudinaryPublicId(publicId);
            request.setAltText(altText);
            request.setVariantId(variantId);
            request.setSortOrder(sortOrder);
            request.setPrimary(primary);
            return ProductImageResponse.fromEntity(imageService.create(productId, request));
        } catch (RuntimeException exception) {
            if (publicId != null) cloudinaryService.deleteImage(publicId);
            throw exception;
        }
    }

    @PutMapping("/{id}")
    public ProductImageResponse update(
            @PathVariable Long productId,
            @PathVariable Long id,
            @Valid @RequestBody ProductImageRequest request) {
        assertOwnership(productId, id);
        ProductImage image = imageService.update(id, request);
        return ProductImageResponse.fromEntity(image);
    }

    @PatchMapping("/{id}/primary")
    public ProductImageResponse setPrimary(@PathVariable Long productId, @PathVariable Long id) {
        assertOwnership(productId, id);
        ProductImage image = imageService.setPrimary(id);
        return ProductImageResponse.fromEntity(image);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long id) {
        assertOwnership(productId, id);
        ProductImage image = imageService.delete(id);
        if (image.getCloudinaryPublicId() != null) cloudinaryService.deleteImage(image.getCloudinaryPublicId());
        return ResponseEntity.noContent().build();
    }

    private void assertOwnership(Long productId, Long imageId) {
        if (!productId.equals(imageService.getImage(imageId).getProduct().getId())) {
            throw new IllegalArgumentException("Product image does not belong to product");
        }
    }
}
