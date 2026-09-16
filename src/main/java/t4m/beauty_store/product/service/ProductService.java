package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.*;
import t4m.beauty_store.product.entity.*;
import t4m.beauty_store.product.repository.*;
import t4m.beauty_store.product.util.Slugifier;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductFacetRepository facetRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductCatalogQueryRepository catalogQueryRepository;
    private final ProductVariantService variantService;

    public record ProductFilter(
        String keyword,
        Long categoryId,
        String category,
        String brand,
        String skinType,
        String concern,
        String hairType,
        String hairConcern,
        String form,
        String finish,
        String ingredient,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        Boolean onSale,
        String sort
    ) {
    }

    /** Public catalog listing; hidden ancestors/brands are excluded as well. */
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return filterProducts(new ProductFilter(null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, "newest"), pageable);
    }

    /** Admin listing with an explicit opt-in for archived products. */
    public Page<Product> getAllProducts(Pageable pageable, boolean includeInactive) {
        return includeInactive ? productRepository.findAll(pageable) : productRepository.findByActiveTrue(pageable);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Product getActiveProductById(Long id) {
        return productRepository.findById(id).filter(this::isPubliclyVisible).orElse(null);
    }

    @Transactional(readOnly = true)
    public Product getActiveProductBySlug(String slug) {
        return productRepository.findBySlug(slug).filter(this::isPubliclyVisible).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        return filterProducts(new ProductFilter(null, categoryId, null, null, null, null, null,
            null, null, null, null, null, null, null, null, "newest"), pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts() {
        return productRepository.findByFeaturedTrueAndActiveTrue().stream()
            .filter(this::isPubliclyVisible)
            .filter(product -> availableStock(product) > 0)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        return filterProducts(new ProductFilter(keyword, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, "newest"), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, Pageable pageable, boolean includeInactive) {
        if (!includeInactive) return searchProducts(keyword, pageable);
        String needle = normalizeForSearch(keyword);
        List<Product> matches = productRepository.findAll().stream()
            .filter(product -> matchesKeyword(product, needle))
            .sorted(Comparator.comparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return page(matches, pageable);
    }

    /** Compatibility overload retained until legacy clients move to beauty facets. */
    @Transactional(readOnly = true)
    public Page<Product> filterProducts(String keyword, Long categoryId, BigDecimal minPrice,
                                        BigDecimal maxPrice, String sortType, Pageable pageable) {
        return filterProducts(new ProductFilter(keyword, categoryId, null, null, null, null, null,
            null, null, null, null, minPrice, maxPrice, null, null, sortType), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> filterProducts(ProductFilter filter, Pageable pageable) {
        validatePriceRange(filter.minPrice(), filter.maxPrice());
        List<Category> categories = categoryRepository.findAll();
        Set<Long> visibleCategoryIds = publiclyVisibleCategoryIds(categories);
        Set<Long> categoryIds = resolveCategoryIds(filter.categoryId(), filter.category(), categories);
        if (categoryIds != null) categoryIds.retainAll(visibleCategoryIds);

        Map<ProductFacetType, String> facetFilters = new EnumMap<>(ProductFacetType.class);
        putFilter(facetFilters, ProductFacetType.SKIN_TYPE, filter.skinType());
        putFilter(facetFilters, ProductFacetType.SKIN_CONCERN, filter.concern());
        putFilter(facetFilters, ProductFacetType.HAIR_TYPE, filter.hairType());
        putFilter(facetFilters, ProductFacetType.HAIR_CONCERN, filter.hairConcern());
        putFilter(facetFilters, ProductFacetType.FORM, filter.form());
        putFilter(facetFilters, ProductFacetType.FINISH, filter.finish());
        putFilter(facetFilters, ProductFacetType.KEY_INGREDIENT, filter.ingredient());

        String keyword = normalizeForSearch(filter.keyword());
        String brand = normalizeForSearch(filter.brand());
        Long brandId = resolveActiveBrandId(brand);
        if (brand != null && brandId == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        ProductCatalogQueryRepository.CatalogQuery query =
            new ProductCatalogQueryRepository.CatalogQuery(
                keyword, visibleCategoryIds, categoryIds, brandId, facetFilters,
                filter.minPrice(), filter.maxPrice(), filter.inStock(), filter.onSale(), filter.sort());
        return hydrate(catalogQueryRepository.findProductIds(query, pageable));
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findByActiveTrue().stream()
            .filter(ProductService::categoryIsPubliclyVisible)
            .toList();
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<ProductFacet> getAllFacets() {
        return facetRepository.findByActiveTrueOrderByTypeAscLabelAsc();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id).orElse(null);
    }

    @Transactional
    public Product createProduct(ProductCreateRequest request) {
        String name = BrandService.required(request.getName(), "Product name is required");
        ProductVariantRequest defaultVariant = defaultVariantRequest(request);
        validateProductMetadata(request.getWarrantyMonths(), request.getPaoMonths(), request.getShelfLifeMonths());

        Brand brandEntity = resolveBrand(request.getBrandId());
        Category category = resolveCategory(request.getCategoryId());
        Set<ProductFacet> facets = resolveFacets(request.getFacetIds());
        Product product = Product.builder()
            .name(name)
            .slug(uniqueSlug(request.getSlug(), name, null))
            .brandEntity(brandEntity)
            .category(category)
            .description(BrandService.clean(request.getDescription()))
            .benefits(BrandService.clean(request.getBenefits()))
            .inci(BrandService.clean(request.getInci()))
            .directions(BrandService.clean(request.getDirections()))
            .warnings(BrandService.clean(request.getWarnings()))
            .origin(BrandService.clean(request.getOrigin()))
            .material(BrandService.clean(request.getMaterial()))
            .warrantyMonths(request.getWarrantyMonths())
            .specifications(BrandService.clean(request.getSpecifications()))
            .spf(BrandService.clean(request.getSpf()))
            .paoMonths(request.getPaoMonths())
            .shelfLifeMonths(request.getShelfLifeMonths())
            .facets(facets)
            .featured(Boolean.TRUE.equals(request.getFeatured()))
            .active(request.getActive() == null || request.getActive())
            .build();
        Product saved = productRepository.save(product);
        variantService.create(saved.getId(), defaultVariant);
        return productRepository.findById(saved.getId()).orElseThrow();
    }

    @Transactional
    public Product updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return null;
        String name = request.getName() == null
            ? product.getName()
            : BrandService.required(request.getName(), "Product name is required");
        validateProductMetadata(
            request.isWarrantyMonthsSpecified() ? request.getWarrantyMonths() : product.getWarrantyMonths(),
            request.isPaoMonthsSpecified() ? request.getPaoMonths() : product.getPaoMonths(),
            request.isShelfLifeMonthsSpecified() ? request.getShelfLifeMonths() : product.getShelfLifeMonths());
        String previousName = product.getName();
        product.setName(name);
        if (request.getSlug() != null || !Objects.equals(name, previousName)) {
            product.setSlug(uniqueSlug(request.getSlug(), name, id));
        } else if (product.getSlug() == null) {
            product.setSlug(uniqueSlug(null, name, id));
        }
        if (request.isBrandIdSpecified() || request.getBrandId() != null) {
            product.setBrandEntity(resolveBrand(request.getBrandId()));
        }
        if (request.isCategoryIdSpecified() || request.getCategoryId() != null) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }
        if (request.isFacetIdsSpecified() || request.getFacetIds() != null) {
            product.setFacets(resolveFacets(request.getFacetIds()));
        }
        setIfSpecified(request.getDescription(), request.isDescriptionSpecified(), product::setDescription);
        setIfSpecified(request.getBenefits(), request.isBenefitsSpecified(), product::setBenefits);
        setIfSpecified(request.getInci(), request.isInciSpecified(), product::setInci);
        setIfSpecified(request.getDirections(), request.isDirectionsSpecified(), product::setDirections);
        setIfSpecified(request.getWarnings(), request.isWarningsSpecified(), product::setWarnings);
        setIfProvided(request.getOrigin(), product::setOrigin);
        setIfProvided(request.getMaterial(), product::setMaterial);
        setIfSpecified(request.getSpecifications(), request.isSpecificationsSpecified(), product::setSpecifications);
        setIfSpecified(request.getSpf(), request.isSpfSpecified(), product::setSpf);
        if (request.isWarrantyMonthsSpecified() || request.getWarrantyMonths() != null) {
            product.setWarrantyMonths(request.getWarrantyMonths());
        }
        if (request.isPaoMonthsSpecified() || request.getPaoMonths() != null) {
            product.setPaoMonths(request.getPaoMonths());
        }
        if (request.isShelfLifeMonthsSpecified() || request.getShelfLifeMonths() != null) {
            product.setShelfLifeMonths(request.getShelfLifeMonths());
        }
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());
        if (request.getActive() != null) product.setActive(request.getActive());
        productRepository.save(product);
        return productRepository.findById(id).orElseThrow();
    }

    @Transactional
    public void deleteProduct(Long id) {
        setProductActive(id, false);
    }

    @Transactional
    public Product setProductActive(Long id, boolean active) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (active && variantRepository.countByProductIdAndActiveTrue(id) == 0) {
            throw new IllegalArgumentException("Cannot restore a product without an active variant");
        }
        product.setActive(active);
        return productRepository.save(product);
    }

    public ProductStockStats getStockStats() {
        List<Product> products = productRepository.findAllByActiveTrue();
        long total = products.size();
        long inStock = products.stream().filter(product -> availableStock(product) > 0).count();
        long outOfStock = total - inStock;
        long lowStock = products.stream().filter(product -> {
            int stock = availableStock(product);
            int threshold = product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
                .map(ProductVariant::getLowStockThreshold).filter(Objects::nonNull)
                .min(Integer::compareTo).orElse(10);
            return stock > 0 && stock <= threshold;
        }).count();
        long totalQuantity = products.stream().mapToLong(this::availableStock).sum();
        return ProductStockStats.builder().totalProducts(total).inStockProducts(inStock)
            .outOfStockProducts(outOfStock).lowStockProducts(lowStock)
            .totalStockQuantity(totalQuantity).build();
    }

    public Page<Product> getOutOfStockProducts(Pageable pageable) {
        return stockPage(product -> availableStock(product) == 0, pageable);
    }

    public Page<Product> getLowStockProducts(int threshold, Pageable pageable) {
        if (threshold < 0) throw new IllegalArgumentException("Threshold must be at least 0");
        return stockPage(product -> availableStock(product) > 0 && availableStock(product) <= threshold, pageable);
    }

    public Page<Product> getInStockProducts(int threshold, Pageable pageable) {
        if (threshold < 0) throw new IllegalArgumentException("Threshold must be at least 0");
        return stockPage(product -> availableStock(product) > threshold, pageable);
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    private Page<Product> stockPage(Predicate<Product> predicate, Pageable pageable) {
        return page(productRepository.findAllByActiveTrue().stream().filter(predicate).toList(), pageable);
    }

    private ProductVariantRequest defaultVariantRequest(ProductCreateRequest request) {
        ProductVariantRequest variant = request.getDefaultVariant();
        if (variant == null) {
            throw new IllegalArgumentException("A default product variant is required");
        }
        if (variant.getDefaultVariant() == null) variant.setDefaultVariant(true);
        return variant;
    }

    private Brand resolveBrand(Long id) {
        if (id == null) return null;
        return brandRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Brand not found"));
    }

    private Category resolveCategory(Long id) {
        if (id == null) return null;
        return categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

    private Set<ProductFacet> resolveFacets(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        List<ProductFacet> facets = facetRepository.findByIdIn(ids);
        if (facets.size() != ids.size()) throw new IllegalArgumentException("One or more product facets were not found");
        if (facets.stream().anyMatch(facet -> !Boolean.TRUE.equals(facet.getActive()))) {
            throw new IllegalArgumentException("Inactive product facets cannot be assigned");
        }
        return new HashSet<>(facets);
    }

    private Set<Long> resolveCategoryIds(Long categoryId, String slugOrId, List<Category> all) {
        if (categoryId == null && BrandService.clean(slugOrId) == null) return null;
        Category root;
        if (categoryId != null) {
            root = all.stream().filter(category -> Objects.equals(category.getId(), categoryId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Category not found"));
        } else {
            String value = normalizeForSearch(slugOrId);
            root = all.stream()
                .filter(category -> Objects.equals(normalizeForSearch(category.getSlug()), value)
                    || Objects.equals(normalizeForSearch(category.getName()), value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        }
        Set<Long> result = new HashSet<>();
        result.add(root.getId());
        boolean changed;
        do {
            changed = false;
            for (Category category : all) {
                if (category.getParent() != null && result.contains(category.getParent().getId())) {
                    changed |= result.add(category.getId());
                }
            }
        } while (changed);
        return result;
    }

    private static Set<Long> publiclyVisibleCategoryIds(List<Category> categories) {
        return categories.stream()
            .filter(ProductService::categoryIsPubliclyVisible)
            .map(Category::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    private Long resolveActiveBrandId(String normalizedBrand) {
        if (normalizedBrand == null) return null;
        return brandRepository.findByActiveTrueOrderByNameAsc().stream()
            .filter(brand -> Objects.equals(normalizeForSearch(brand.getSlug()), normalizedBrand)
                || Objects.equals(normalizeForSearch(brand.getName()), normalizedBrand))
            .map(Brand::getId)
            .findFirst()
            .orElse(null);
    }

    private Page<Product> hydrate(Page<Long> idPage) {
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), idPage.getPageable(), idPage.getTotalElements());
        }
        Map<Long, Product> productsById = productRepository.findCatalogPageByIdIn(idPage.getContent()).stream()
            .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
        List<Product> ordered = idPage.getContent().stream()
            .map(productsById::get)
            .filter(Objects::nonNull)
            .toList();
        return new PageImpl<>(ordered, idPage.getPageable(), idPage.getTotalElements());
    }

    private boolean isPubliclyVisible(Product product) {
        if (!Boolean.TRUE.equals(product.getActive())) return false;
        if (product.getBrandEntity() != null && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) return false;
        return categoryIsPubliclyVisible(product.getCategory());
    }

    private static boolean categoryIsPubliclyVisible(Category category) {
        Category cursor = category;
        int guard = 0;
        while (cursor != null && guard++ < 32) {
            if (!Boolean.TRUE.equals(cursor.getActive())) return false;
            cursor = cursor.getParent();
        }
        return guard < 32;
    }

    private static void putFilter(Map<ProductFacetType, String> filters, ProductFacetType type, String value) {
        String normalized = normalizeForSearch(value);
        if (normalized != null) filters.put(type, normalized);
    }

    private static boolean matchesKeyword(Product product, String needle) {
        if (needle == null) return true;
        return contains(product.getName(), needle) || contains(product.getSlug(), needle)
            || contains(product.getDescription(), needle) || contains(product.getBenefits(), needle)
            || contains(product.getInci(), needle)
            || (product.getBrandEntity() != null && contains(product.getBrandEntity().getName(), needle))
            || (product.getFacets() != null && product.getFacets().stream()
                .filter(facet -> Boolean.TRUE.equals(facet.getActive()))
                .anyMatch(facet -> contains(facet.getCode(), needle) || contains(facet.getLabel(), needle)))
            || (product.getVariants() != null && product.getVariants().stream()
                .anyMatch(variant -> contains(variant.getSku(), needle) || contains(variant.getBarcode(), needle)));
    }

    private static boolean contains(String value, String needle) {
        String normalized = normalizeForSearch(value);
        return normalized != null && normalized.contains(needle);
    }

    private int availableStock(Product product) {
        List<ProductVariant> active = product.getVariants() == null ? List.of() : product.getVariants().stream()
            .filter(variant -> Boolean.TRUE.equals(variant.getActive())).toList();
        return active.stream().mapToInt(ProductVariant::getAvailableStock).sum();
    }

    private List<BigDecimal> effectivePrices(Product product) {
        List<BigDecimal> prices = product.getVariants() == null ? List.of() : product.getVariants().stream()
            .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
            .map(ProductVariant::getEffectivePrice).filter(Objects::nonNull).toList();
        return prices;
    }

    private boolean isOnSale(Product product) {
        return product.getVariants() != null && product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
                .anyMatch(variant -> variant.getDiscountPrice() != null
                    && variant.getPrice() != null
                    && variant.getDiscountPrice().compareTo(variant.getPrice()) < 0);
    }

    private Comparator<Product> comparator(String sort) {
        Comparator<Product> newest = Comparator.comparing(Product::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort == null ? "newest" : sort.toLowerCase(Locale.ROOT)) {
            case "price-asc" -> Comparator.comparing(this::minimumPrice, Comparator.nullsLast(BigDecimal::compareTo));
            case "price-desc" -> Comparator.comparing(this::maximumPrice,
                Comparator.nullsLast(BigDecimal::compareTo)).reversed();
            case "name", "name-asc" -> Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER);
            case "rating", "rating-desc" -> Comparator.comparing(Product::getAverageRating,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(newest);
            default -> newest;
        };
    }

    private BigDecimal minimumPrice(Product product) {
        return effectivePrices(product).stream().min(BigDecimal::compareTo).orElse(null);
    }

    private BigDecimal maximumPrice(Product product) {
        return effectivePrices(product).stream().max(BigDecimal::compareTo).orElse(null);
    }

    private String uniqueSlug(String requested, String name, Long currentId) {
        String base = Slugifier.slugify(BrandService.clean(requested) == null ? name : requested);
        String candidate = base;
        int suffix = 2;
        while (currentId == null
            ? productRepository.existsBySlugIgnoreCase(candidate)
            : productRepository.existsBySlugIgnoreCaseAndIdNot(candidate, currentId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static void validateProductMetadata(Integer warranty, Integer pao, Integer shelfLife) {
        if (warranty != null && warranty < 0) throw new IllegalArgumentException("Warranty months must be at least 0");
        if (pao != null && pao < 0) throw new IllegalArgumentException("PAO months must be at least 0");
        if (shelfLife != null && shelfLife < 0) throw new IllegalArgumentException("Shelf life months must be at least 0");
    }

    private static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.compareTo(ZERO) < 0) throw new IllegalArgumentException("Minimum price must be at least 0");
        if (maxPrice != null && maxPrice.compareTo(ZERO) < 0) throw new IllegalArgumentException("Maximum price must be at least 0");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Minimum price must not exceed maximum price");
        }
    }

    private static String normalizeForSearch(String value) {
        String clean = BrandService.clean(value);
        return clean == null ? null : clean.toLowerCase(Locale.ROOT);
    }

    private static void setIfProvided(String value, java.util.function.Consumer<String> setter) {
        if (value != null) setter.accept(BrandService.clean(value));
    }

    private static void setIfSpecified(
            String value, boolean specified, java.util.function.Consumer<String> setter) {
        if (specified || value != null) setter.accept(BrandService.clean(value));
    }

    private static Page<Product> page(List<Product> products, Pageable pageable) {
        long offset = pageable.getOffset();
        if (offset >= products.size() || offset > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, products.size());
        }
        int start = (int) offset;
        int end = (int) Math.min(offset + pageable.getPageSize(), products.size());
        return new PageImpl<>(products.subList(start, end), pageable, products.size());
    }
}
