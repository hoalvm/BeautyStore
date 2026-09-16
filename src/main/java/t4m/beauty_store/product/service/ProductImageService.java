package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.ProductImageRequest;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductImage;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.ProductImageRepository;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public List<ProductImage> getImages(Long productId) {
        requireProduct(productId);
        return imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
    }

    public ProductImage getImage(Long id) {
        return imageRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product image not found"));
    }

    @Transactional
    public ProductImage create(Long productId, ProductImageRequest request) {
        Product product = requireProduct(productId);
        ProductVariant variant = resolveVariant(productId, request.getVariantId());
        enforceLimit(productId, variant, null);
        ProductImage image = ProductImage.builder()
            .product(product)
            .variant(variant)
            .url(BrandService.required(request.getUrl(), "Image URL is required"))
            .cloudinaryPublicId(BrandService.clean(request.getCloudinaryPublicId()))
            .altText(BrandService.required(request.getAltText(), "Image alt text is required"))
            .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
            .primary(Boolean.TRUE.equals(request.getPrimary()))
            .build();
        if (Boolean.TRUE.equals(image.getPrimary())) clearPrimary(productId, variant, null);
        ProductImage saved = imageRepository.save(image);
        product.getImages().add(saved);
        return saved;
    }

    @Transactional
    public ProductImage update(Long id, ProductImageRequest request) {
        ProductImage image = imageRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product image not found"));
        Long productId = image.getProduct().getId();
        ProductVariant variant = resolveVariant(productId, request.getVariantId());
        enforceLimit(productId, variant, id);
        if (Boolean.TRUE.equals(request.getPrimary())) clearPrimary(productId, variant, id);
        image.setVariant(variant);
        image.setUrl(BrandService.required(request.getUrl(), "Image URL is required"));
        // Metadata-only edits/reordering must not orphan the existing Cloudinary asset.
        if (request.getCloudinaryPublicId() != null) {
            image.setCloudinaryPublicId(BrandService.clean(request.getCloudinaryPublicId()));
        }
        image.setAltText(BrandService.required(request.getAltText(), "Image alt text is required"));
        image.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        image.setPrimary(Boolean.TRUE.equals(request.getPrimary()));
        return imageRepository.save(image);
    }

    @Transactional
    public ProductImage setPrimary(Long id) {
        ProductImage image = imageRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product image not found"));
        clearPrimary(image.getProduct().getId(), image.getVariant(), id);
        image.setPrimary(true);
        return imageRepository.save(image);
    }

    @Transactional
    public ProductImage delete(Long id) {
        ProductImage image = imageRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product image not found"));
        Product product = image.getProduct();
        ProductVariant variant = image.getVariant();
        boolean wasPrimary = Boolean.TRUE.equals(image.getPrimary());
        imageRepository.delete(image);
        imageRepository.flush();
        if (wasPrimary) {
            List<ProductImage> remaining = variant == null
                ? imageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                    .filter(candidate -> candidate.getVariant() == null).toList()
                : imageRepository.findByVariantIdOrderBySortOrderAscIdAsc(variant.getId());
            if (!remaining.isEmpty()) {
                ProductImage replacement = remaining.getFirst();
                replacement.setPrimary(true);
                imageRepository.save(replacement);
            }
        }
        return image;
    }

    private ProductVariant resolveVariant(Long productId, Long variantId) {
        if (variantId == null) return null;
        ProductVariant variant = variantRepository.findById(variantId)
            .orElseThrow(() -> new IllegalArgumentException("Product variant not found"));
        if (!productId.equals(variant.getProduct().getId())) {
            throw new IllegalArgumentException("Image variant does not belong to product");
        }
        return variant;
    }

    private void enforceLimit(Long productId, ProductVariant variant, Long currentId) {
        long count = variant == null
            ? imageRepository.countByProductIdAndVariantIsNull(productId)
            : imageRepository.countByVariantId(variant.getId());
        boolean changingScope = currentId == null || imageRepository.findById(currentId)
            .map(current -> current.getVariant() == null
                ? variant != null
                : variant == null || !current.getVariant().getId().equals(variant.getId()))
            .orElse(true);
        if (changingScope && count >= (variant == null ? 8 : 3)) {
            throw new IllegalArgumentException(variant == null
                ? "A product can have at most 8 gallery images"
                : "A product variant can have at most 3 images");
        }
    }

    private void clearPrimary(Long productId, ProductVariant variant, Long exceptId) {
        List<ProductImage> scope = variant == null
            ? imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .filter(image -> image.getVariant() == null).toList()
            : imageRepository.findByVariantIdOrderBySortOrderAscIdAsc(variant.getId());
        scope.stream()
            .filter(image -> exceptId == null || !exceptId.equals(image.getId()))
            .forEach(image -> image.setPrimary(false));
        imageRepository.saveAll(scope);
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
}
