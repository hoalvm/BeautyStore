package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.ProductVariantRequest;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    public List<ProductVariant> getVariants(Long productId, boolean includeInactive) {
        requireProduct(productId);
        return includeInactive
            ? variantRepository.findByProductIdOrderByDefaultVariantDescIdAsc(productId)
            : variantRepository.findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(productId);
    }

    public ProductVariant getVariant(Long id) {
        return variantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product variant not found"));
    }

    @Transactional
    public ProductVariant create(Long productId, ProductVariantRequest request) {
        Product product = requireProductForUpdate(productId);
        validate(request, null);
        boolean firstActive = variantRepository.countByProductIdAndActiveTrue(productId) == 0;
        boolean active = request.getActive() == null || request.getActive();
        if (!active && firstActive) {
            throw new IllegalArgumentException("The first product variant must be active");
        }
        boolean makeDefault = active && (firstActive || Boolean.TRUE.equals(request.getDefaultVariant()));
        if (makeDefault) clearDefault(productId, null);
        ProductVariant variant = ProductVariant.builder()
            .product(product)
            .sku(normalizeSku(request.getSku()))
            .barcode(BrandService.clean(request.getBarcode()))
            .label(BrandService.clean(request.getLabel()))
            .shadeName(BrandService.clean(request.getShadeName()))
            .shadeHex(normalizeHex(request.getShadeHex()))
            .sizeValue(request.getSizeValue())
            .sizeUnit(BrandService.clean(request.getSizeUnit()))
            .price(request.getPrice())
            .discountPrice(request.getDiscountPrice())
            .lowStockThreshold(request.getLowStockThreshold() == null ? 10 : request.getLowStockThreshold())
            .defaultVariant(makeDefault)
            .active(active)
            .build();
        ProductVariant saved = variantRepository.save(variant);
        product.getVariants().add(saved);
        ensureDefault(productId);
        return saved;
    }

    @Transactional
    public ProductVariant update(Long id, ProductVariantRequest request) {
        ProductVariant variant = getVariant(id);
        requireProductForUpdate(variant.getProduct().getId());
        validate(request, id);
        if (Boolean.FALSE.equals(request.getActive()) && Boolean.TRUE.equals(variant.getActive())
                && variantRepository.countByProductIdAndActiveTrue(variant.getProduct().getId()) <= 1
                && Boolean.TRUE.equals(variant.getProduct().getActive())) {
            throw new IllegalArgumentException("An active product must have at least one active variant");
        }
        boolean resultingActive = request.getActive() == null
            ? Boolean.TRUE.equals(variant.getActive())
            : Boolean.TRUE.equals(request.getActive());
        if (Boolean.TRUE.equals(request.getDefaultVariant()) && !resultingActive) {
            throw new IllegalArgumentException("An inactive product variant cannot be the default");
        }
        if (Boolean.TRUE.equals(request.getDefaultVariant())) {
            clearDefault(variant.getProduct().getId(), id);
        }
        variant.setSku(normalizeSku(request.getSku()));
        variant.setBarcode(BrandService.clean(request.getBarcode()));
        variant.setLabel(BrandService.clean(request.getLabel()));
        variant.setShadeName(BrandService.clean(request.getShadeName()));
        variant.setShadeHex(normalizeHex(request.getShadeHex()));
        variant.setSizeValue(request.getSizeValue());
        variant.setSizeUnit(BrandService.clean(request.getSizeUnit()));
        variant.setPrice(request.getPrice());
        variant.setDiscountPrice(request.getDiscountPrice());
        variant.setLowStockThreshold(request.getLowStockThreshold() == null ? 10 : request.getLowStockThreshold());
        if (request.getDefaultVariant() != null) variant.setDefaultVariant(request.getDefaultVariant());
        if (request.getActive() != null) variant.setActive(request.getActive());
        ProductVariant saved = variantRepository.save(variant);
        ensureDefault(variant.getProduct().getId());
        return saved;
    }

    @Transactional
    public ProductVariant setActive(Long id, boolean active) {
        ProductVariant variant = getVariant(id);
        Long productId = variant.getProduct().getId();
        requireProductForUpdate(productId);
        if (!active && Boolean.TRUE.equals(variant.getActive())
                && variantRepository.countByProductIdAndActiveTrue(productId) <= 1
                && Boolean.TRUE.equals(variant.getProduct().getActive())) {
            throw new IllegalArgumentException("An active product must have at least one active variant");
        }
        variant.setActive(active);
        variantRepository.save(variant);
        ensureDefault(productId);
        return variant;
    }

    private void clearDefault(Long productId, Long exceptId) {
        List<ProductVariant> variants = variantRepository.findByProductIdOrderByDefaultVariantDescIdAsc(productId);
        variants.stream()
            .filter(variant -> exceptId == null || !exceptId.equals(variant.getId()))
            .forEach(variant -> variant.setDefaultVariant(false));
        variantRepository.saveAll(variants);
    }

    private void ensureDefault(Long productId) {
        List<ProductVariant> variants = variantRepository
            .findByProductIdOrderByDefaultVariantDescIdAsc(productId);
        List<ProductVariant> active = variants.stream()
            .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
            .toList();
        if (variants.isEmpty()) return;
        List<ProductVariant> eligible = active.isEmpty() ? variants : active;
        ProductVariant selected = eligible.stream()
            .filter(candidate -> Boolean.TRUE.equals(candidate.getDefaultVariant()))
            .findFirst()
            .orElse(eligible.getFirst());
        variants.forEach(candidate -> candidate.setDefaultVariant(candidate == selected));
        variantRepository.saveAll(variants);
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private Product requireProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private void validate(ProductVariantRequest request, Long currentId) {
        String sku = normalizeSku(request.getSku());
        if (sku == null) throw new IllegalArgumentException("Variant SKU is required");
        boolean duplicateSku = currentId == null
            ? variantRepository.existsBySkuIgnoreCase(sku)
            : variantRepository.existsBySkuIgnoreCaseAndIdNot(sku, currentId);
        if (duplicateSku) throw new IllegalArgumentException("Variant SKU already exists");
        String barcode = BrandService.clean(request.getBarcode());
        if (barcode != null) {
            boolean duplicateBarcode = currentId == null
                ? variantRepository.existsByBarcodeIgnoreCase(barcode)
                : variantRepository.existsByBarcodeIgnoreCaseAndIdNot(barcode, currentId);
            if (duplicateBarcode) throw new IllegalArgumentException("Variant barcode already exists");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Variant price must be at least 0");
        }
        if (request.getDiscountPrice() != null
                && (request.getDiscountPrice().compareTo(ZERO) < 0
                    || request.getDiscountPrice().compareTo(request.getPrice()) >= 0)) {
            throw new IllegalArgumentException("Discount price must be lower than variant price and at least 0");
        }
        if (request.getSizeValue() != null && request.getSizeValue().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Variant size must be greater than 0");
        }
        if ((request.getSizeValue() == null) != (BrandService.clean(request.getSizeUnit()) == null)) {
            throw new IllegalArgumentException("Variant size value and unit must be provided together");
        }
        String hex = normalizeHex(request.getShadeHex());
        if (hex != null && !hex.matches("^#[0-9A-F]{6}$")) {
            throw new IllegalArgumentException("Shade color must use #RRGGBB");
        }
        if (request.getLowStockThreshold() != null && request.getLowStockThreshold() < 0) {
            throw new IllegalArgumentException("Low-stock threshold must be at least 0");
        }
    }

    private static String normalizeSku(String value) {
        String clean = BrandService.clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private static String normalizeHex(String value) {
        String clean = BrandService.clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }
}
