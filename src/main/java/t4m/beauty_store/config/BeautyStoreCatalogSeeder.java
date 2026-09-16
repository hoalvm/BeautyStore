package t4m.beauty_store.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.product.entity.*;
import t4m.beauty_store.product.repository.*;
import t4m.beauty_store.product.util.Slugifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
@Profile({"dev", "test"})
@Order(10)
@RequiredArgsConstructor
public class BeautyStoreCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BeautyStoreCatalogSeeder.class);

    private static final Set<String> LEGACY_CATEGORY_NAMES = Set.of(
        "Xe & Phi thuyền", "Robot & Công nghệ", "Búp bê & Nhà búp bê", "Đồ chơi giáo dục",
        "Đồ chơi ngoài trời", "Mô hình & Sưu tập", "Đồ chơi sáng tạo", "Đồ chơi cho bé",
        "Bút viết & dụng cụ cơ bản", "Vở giấy & sổ tay", "Balo hộp bút & phụ kiện",
        "Mỹ thuật & sáng tạo", "STEM & thí nghiệm", "Máy tính & thiết bị học tập",
        "Sách tham khảo & flashcard", "Combo theo lớp"
    );

    private static final List<CategorySeed> CATEGORIES = List.of(
        new CategorySeed("Chăm sóc da mặt", "cham-soc-da-mat", "Sản phẩm làm sạch, dưỡng ẩm và phục hồi da.", "✨"),
        new CategorySeed("Chống nắng", "chong-nang", "Kem và sữa chống nắng dùng hằng ngày.", "☀️"),
        new CategorySeed("Trang điểm", "trang-diem", "Sản phẩm trang điểm nền, môi, mắt và má.", "💄"),
        new CategorySeed("Chăm sóc cơ thể", "cham-soc-co-the", "Làm sạch và dưỡng ẩm cơ thể.", "🫧"),
        new CategorySeed("Chăm sóc tóc", "cham-soc-toc", "Chăm sóc da đầu và phục hồi mái tóc.", "🌿"),
        new CategorySeed("Nước hoa", "nuoc-hoa", "Hương thơm cá nhân cho nhiều phong cách.", "🌸"),
        new CategorySeed("Dụng cụ & phụ kiện", "dung-cu-phu-kien", "Cọ, mút, dụng cụ và phụ kiện làm đẹp.", "🪞"),
        new CategorySeed("Bộ quà tặng & minisize", "bo-qua-tang-minisize", "Bộ dùng thử và quà tặng chăm sóc bản thân.", "🎁")
    );

    private static final List<BrandSeed> BRANDS = List.of(
        new BrandSeed("Aurora Botanics", "aurora-botanics", "Việt Nam"),
        new BrandSeed("Lune Laboratory", "lune-laboratory", "Hàn Quốc"),
        new BrandSeed("Mộc An", "moc-an", "Việt Nam"),
        new BrandSeed("Rosée Atelier", "rosee-atelier", "Pháp"),
        new BrandSeed("Veluna", "veluna", "Nhật Bản"),
        new BrandSeed("Nami Beauty", "nami-beauty", "Hàn Quốc"),
        new BrandSeed("Dew & Bloom", "dew-and-bloom", "Úc"),
        new BrandSeed("Belle Mini", "belle-mini", "Việt Nam")
    );

    private static final List<FacetSeed> FACETS = List.of(
        facet(ProductFacetType.SKIN_TYPE, "all-skin", "Mọi loại da"),
        facet(ProductFacetType.SKIN_TYPE, "oily", "Da dầu"),
        facet(ProductFacetType.SKIN_TYPE, "dry", "Da khô"),
        facet(ProductFacetType.SKIN_TYPE, "sensitive", "Da nhạy cảm"),
        facet(ProductFacetType.SKIN_CONCERN, "hydration", "Cấp ẩm"),
        facet(ProductFacetType.SKIN_CONCERN, "acne", "Da mụn"),
        facet(ProductFacetType.SKIN_CONCERN, "brightening", "Làm sáng"),
        facet(ProductFacetType.SKIN_CONCERN, "barrier", "Phục hồi hàng rào da"),
        facet(ProductFacetType.HAIR_TYPE, "all-hair", "Mọi loại tóc"),
        facet(ProductFacetType.HAIR_TYPE, "dry-hair", "Tóc khô"),
        facet(ProductFacetType.HAIR_CONCERN, "hair-fall", "Tóc gãy rụng"),
        facet(ProductFacetType.HAIR_CONCERN, "damaged-hair", "Tóc hư tổn"),
        facet(ProductFacetType.FORM, "serum", "Tinh chất"),
        facet(ProductFacetType.FORM, "cream", "Dạng kem"),
        facet(ProductFacetType.FORM, "gel", "Dạng gel"),
        facet(ProductFacetType.FORM, "liquid", "Dạng lỏng"),
        facet(ProductFacetType.FINISH, "natural", "Tự nhiên"),
        facet(ProductFacetType.FINISH, "matte", "Lì"),
        facet(ProductFacetType.FINISH, "glowy", "Căng bóng"),
        facet(ProductFacetType.COVERAGE, "light", "Độ che phủ nhẹ"),
        facet(ProductFacetType.COVERAGE, "medium", "Độ che phủ vừa"),
        facet(ProductFacetType.FRAGRANCE_FAMILY, "floral", "Hương hoa"),
        facet(ProductFacetType.FRAGRANCE_FAMILY, "fresh", "Hương tươi mát"),
        facet(ProductFacetType.FRAGRANCE_FAMILY, "woody", "Hương gỗ"),
        facet(ProductFacetType.KEY_INGREDIENT, "hyaluronic-acid", "Hyaluronic Acid"),
        facet(ProductFacetType.KEY_INGREDIENT, "niacinamide", "Niacinamide"),
        facet(ProductFacetType.KEY_INGREDIENT, "centella", "Rau má"),
        facet(ProductFacetType.KEY_INGREDIENT, "ceramide", "Ceramide")
    );

    private static final List<ProductSeed> PRODUCTS = buildProducts();

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductFacetRepository facetRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryBatchRepository batchRepository;
    private final ProductImageRepository imageRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        archiveLegacyCatalog();
        Map<String, Category> categories = seedCategories();
        Map<String, Brand> brands = seedBrands();
        Map<String, ProductFacet> facets = seedFacets();
        int created = seedProducts(categories, brands, facets);
        log.info("BeautyStore demo catalog ready: {} categories, {} demo products ({} newly created)",
            categories.size(), PRODUCTS.size(), created);
    }

    private void archiveLegacyCatalog() {
        List<Category> categories = categoryRepository.findAll();
        categories.stream().filter(category -> LEGACY_CATEGORY_NAMES.contains(category.getName()))
            .forEach(category -> category.setActive(false));
        categoryRepository.saveAll(categories);

        List<Product> products = productRepository.findAll();
        products.stream().filter(product -> {
            boolean educationSku = product.getVariants() != null && product.getVariants().stream()
                .map(ProductVariant::getSku)
                .map(BeautyStoreCatalogSeeder::normalizedSku)
                .filter(Objects::nonNull)
                .anyMatch(sku -> sku.startsWith("EDU-"));
            boolean legacyCategory = product.getCategory() != null
                && LEGACY_CATEGORY_NAMES.contains(product.getCategory().getName());
            return educationSku || legacyCategory;
        }).forEach(product -> {
            product.setActive(false);
            if (product.getVariants() != null) {
                product.getVariants().forEach(variant -> {
                    variant.setActive(false);
                    if (variant.getBatches() != null) {
                        variant.getBatches().forEach(batch -> batch.setActive(false));
                    }
                });
            }
        });
        productRepository.saveAll(products);
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> result = new HashMap<>();
        for (int index = 0; index < CATEGORIES.size(); index++) {
            CategorySeed seed = CATEGORIES.get(index);
            Category category = categoryRepository.findBySlugIgnoreCase(seed.slug())
                .or(() -> categoryRepository.findByNameIgnoreCase(seed.name()))
                .orElse(null);
            if (category == null) {
                category = Category.builder().name(seed.name()).slug(seed.slug())
                    .description(seed.description()).icon(seed.icon()).displayOrder(index)
                    .active(true).build();
                category = categoryRepository.save(category);
            }
            result.put(seed.slug(), category);
        }
        return result;
    }

    private Map<String, Brand> seedBrands() {
        Map<String, Brand> result = new HashMap<>();
        for (BrandSeed seed : BRANDS) {
            Brand brand = brandRepository.findBySlugIgnoreCase(seed.slug())
                .or(() -> brandRepository.findByNameIgnoreCase(seed.name()))
                .orElse(null);
            if (brand == null) {
                brand = brandRepository.save(Brand.builder().name(seed.name()).slug(seed.slug())
                    .description("Thương hiệu mỹ phẩm demo hư cấu dùng cho môi trường phát triển.")
                    .country(seed.country()).active(true).build());
            }
            result.put(seed.slug(), brand);
        }
        return result;
    }

    private Map<String, ProductFacet> seedFacets() {
        Map<String, ProductFacet> result = new HashMap<>();
        for (FacetSeed seed : FACETS) {
            ProductFacet facet = facetRepository.findByTypeAndCodeIgnoreCase(seed.type(), seed.code())
                .orElse(null);
            if (facet == null) {
                facet = facetRepository.save(ProductFacet.builder().type(seed.type()).code(seed.code())
                    .label(seed.label()).active(true).build());
            }
            result.put(facetKey(seed.type(), seed.code()), facet);
        }
        return result;
    }

    private int seedProducts(Map<String, Category> categories, Map<String, Brand> brands,
                             Map<String, ProductFacet> facets) {
        int created = 0;
        for (ProductSeed seed : PRODUCTS) {
            if (variantRepository.existsBySkuIgnoreCase(seed.sku())) {
                continue;
            }
            Category category = categories.get(seed.categorySlug());
            Brand brand = brands.get(seed.brandSlug());
            Set<ProductFacet> assigned = new HashSet<>();
            for (FacetRef reference : seed.facets()) {
                ProductFacet facet = facets.get(facetKey(reference.type(), reference.code()));
                if (facet != null && Boolean.TRUE.equals(facet.getActive())) assigned.add(facet);
            }
            Product product = Product.builder()
                .name(seed.name()).slug(uniqueProductSlug(seed.name())).description(seed.description())
                .benefits(seed.benefits()).inci(seed.inci()).directions(seed.directions())
                .warnings("Chỉ dùng ngoài da. Ngưng sử dụng nếu xuất hiện kích ứng.")
                .origin(brand.getCountry()).spf(seed.spf()).paoMonths(12).shelfLifeMonths(36)
                .category(category).brandEntity(brand).facets(assigned)
                .featured(seed.featured()).active(true).build();
            product = productRepository.save(product);

            ProductVariant variant = ProductVariant.builder().product(product).sku(seed.sku())
                .label(seed.variantLabel()).shadeName(seed.shadeName()).shadeHex(seed.shadeHex())
                .sizeValue(seed.sizeValue()).sizeUnit(seed.sizeUnit()).price(seed.price())
                .discountPrice(seed.discountPrice()).lowStockThreshold(10).defaultVariant(true)
                .active(true).build();
            variant = variantRepository.save(variant);

            InventoryBatch batch = InventoryBatch.builder().variant(variant)
                .batchCode("DEMO-" + seed.sku()).manufacturedDate(LocalDate.now().minusMonths(2))
                .expiryDate(LocalDate.now().plusMonths(30)).quantityOnHand(seed.stock())
                .quantityReserved(0).active(true).build();
            batchRepository.save(batch);

            ProductImage image = ProductImage.builder().product(product).url(imageForCategory(seed.categorySlug()))
                .altText(seed.name()).sortOrder(0).primary(true).build();
            imageRepository.save(image);
            created++;
        }
        return created;
    }

    private String uniqueProductSlug(String name) {
        String base = Slugifier.slugify(name);
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlugIgnoreCase(candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }

    private static String imageForCategory(String categorySlug) {
        return switch (categorySlug) {
            case "cham-soc-da-mat", "chong-nang" -> "/images/beauty/catalog-skincare.webp";
            case "trang-diem" -> "/images/beauty/catalog-makeup.webp";
            case "cham-soc-co-the", "cham-soc-toc" -> "/images/beauty/catalog-body-hair.webp";
            case "nuoc-hoa", "dung-cu-phu-kien", "bo-qua-tang-minisize" ->
                "/images/beauty/catalog-gift-tools.webp";
            default -> "/images/beauty/hero-beautystore-v2.webp";
        };
    }

    private static List<ProductSeed> buildProducts() {
        List<ProductSeed> products = new ArrayList<>();
        addCategory(products, "cham-soc-da-mat", "SKIN", List.of(
            "Gel rửa mặt dịu nhẹ", "Nước cân bằng phục hồi", "Tinh chất HA cấp ẩm",
            "Kem dưỡng Ceramide", "Mặt nạ ngủ rau má"), ProductFacetType.SKIN_CONCERN, "hydration");
        addCategory(products, "chong-nang", "SUN", List.of(
            "Kem chống nắng kiềm dầu SPF50+", "Sữa chống nắng da nhạy cảm SPF50+",
            "Gel chống nắng cấp ẩm SPF50+", "Thỏi chống nắng tiện lợi SPF50+",
            "Kem chống nắng nâng tông SPF50+"), ProductFacetType.SKIN_TYPE, "all-skin");
        addCategory(products, "trang-diem", "MAKE", List.of(
            "Son tint nhung màu Hồng Trà", "Kem nền mỏng nhẹ màu Ivory",
            "Phấn má kem màu Rose", "Mascara dài mi chống lem", "Bảng mắt bốn màu Hoàng Hôn"),
            ProductFacetType.FINISH, "natural");
        addCategory(products, "cham-soc-co-the", "BODY", List.of(
            "Sữa tắm hoa trà", "Lotion cơ thể yến mạch", "Tẩy tế bào chết đường nâu",
            "Kem dưỡng tay bơ hạt mỡ", "Dầu dưỡng thể ánh ngọc"), ProductFacetType.SKIN_TYPE, "all-skin");
        addCategory(products, "cham-soc-toc", "HAIR", List.of(
            "Dầu gội cân bằng da đầu", "Dầu xả phục hồi tóc khô", "Mặt nạ tóc Keratin",
            "Tinh chất dưỡng tóc nhẹ", "Xịt bảo vệ tóc trước nhiệt"), ProductFacetType.HAIR_TYPE, "all-hair");
        addCategory(products, "nuoc-hoa", "FRAG", List.of(
            "Nước hoa Morning Petal", "Nước hoa Citrus Veil", "Nước hoa Velvet Wood",
            "Nước hoa Moonlit Tea", "Xịt thơm cơ thể Pure Cotton"),
            ProductFacetType.FRAGRANCE_FAMILY, "fresh");
        addCategory(products, "dung-cu-phu-kien", "TOOL", List.of(
            "Bộ cọ trang điểm năm món", "Mút tán nền hình giọt nước", "Kẹp mi thép không gỉ",
            "Băng đô rửa mặt mềm", "Túi mỹ phẩm chống thấm"), null, null);
        addCategory(products, "bo-qua-tang-minisize", "GIFT", List.of(
            "Bộ minisize dưỡng ẩm ba bước", "Bộ quà tặng chăm sóc cơ thể",
            "Bộ son tint ba màu", "Bộ nước hoa discovery", "Combo du lịch chăm sóc tóc"),
            ProductFacetType.SKIN_TYPE, "all-skin");
        if (products.size() != 40) throw new IllegalStateException("Beauty demo catalog must contain 40 products");
        return List.copyOf(products);
    }

    private static void addCategory(List<ProductSeed> target, String category, String skuGroup,
                                    List<String> names, ProductFacetType facetType, String facetCode) {
        int categoryIndex = CATEGORIES.stream().map(CategorySeed::slug).toList().indexOf(category);
        for (int index = 0; index < names.size(); index++) {
            int number = index + 1;
            BigDecimal price = BigDecimal.valueOf(149_000L + categoryIndex * 35_000L + index * 27_000L);
            BigDecimal discount = number % 2 == 0 ? price.subtract(BigDecimal.valueOf(20_000)) : null;
            String brandSlug = BRANDS.get((categoryIndex + index) % BRANDS.size()).slug();
            boolean isTool = "TOOL".equals(skuGroup);
            boolean isMakeup = "MAKE".equals(skuGroup);
            String shadeName = isMakeup && number <= 3 ? List.of("Hồng Trà", "Ivory", "Rose").get(index) : null;
            String shadeHex = isMakeup && number <= 3 ? List.of("#B85C72", "#F1D5BD", "#D88A98").get(index) : null;
            String unit = isTool ? "bộ" : ("FRAG".equals(skuGroup) ? "ml" : "ml");
            BigDecimal size = isTool ? BigDecimal.ONE : BigDecimal.valueOf("FRAG".equals(skuGroup) ? 30 : 50);
            String spf = "SUN".equals(skuGroup) ? "SPF50+ PA++++" : null;
            List<FacetRef> refs = new ArrayList<>();
            if (facetType != null && facetCode != null) {
                String selectedCode = "FRAG".equals(skuGroup)
                    ? List.of("floral", "fresh", "woody", "fresh", "fresh").get(index)
                    : facetCode;
                refs.add(new FacetRef(facetType, selectedCode));
            }
            if (Set.of("SKIN", "SUN", "BODY", "GIFT").contains(skuGroup)) {
                refs.add(new FacetRef(ProductFacetType.SKIN_TYPE, "all-skin"));
            }
            if (!isTool) {
                String form = switch (skuGroup) {
                    case "SKIN" -> List.of("gel", "liquid", "serum", "cream", "cream").get(index);
                    case "SUN" -> index == 2 ? "gel" : "cream";
                    case "MAKE" -> index == 1 || index == 2 ? "cream" : "liquid";
                    case "BODY" -> index == 1 || index == 3 ? "cream" : "liquid";
                    default -> "liquid";
                };
                refs.add(new FacetRef(ProductFacetType.FORM, form));
            }
            if ("HAIR".equals(skuGroup) && number <= 3) {
                refs.add(new FacetRef(ProductFacetType.HAIR_CONCERN,
                    number == 1 ? "hair-fall" : "damaged-hair"));
            }
            if ("SKIN".equals(skuGroup) && number == 4) refs.add(new FacetRef(ProductFacetType.KEY_INGREDIENT, "ceramide"));
            if ("SKIN".equals(skuGroup) && number == 5) refs.add(new FacetRef(ProductFacetType.KEY_INGREDIENT, "centella"));
            target.add(new ProductSeed(category, brandSlug,
                "BEA-DEMO-" + skuGroup + "-" + String.format("%03d", number), names.get(index),
                "Sản phẩm demo BeautyStore với thông tin minh bạch, phù hợp để kiểm thử catalog.",
                "Hỗ trợ chăm sóc cá nhân hằng ngày và mang lại trải nghiệm sử dụng dễ chịu.",
                isTool ? "Không áp dụng." : "Aqua, Glycerin, Panthenol, Tocopherol.",
                isTool ? "Vệ sinh trước và sau mỗi lần sử dụng." : "Dùng lượng vừa đủ trên vùng da sạch.",
                spf, size, unit, shadeName == null ? size.stripTrailingZeros().toPlainString() + " " + unit : shadeName,
                shadeName, shadeHex, price, discount, 24 + categoryIndex * 3 + index,
                number == 1, List.copyOf(refs)));
        }
    }

    private static String normalizedSku(String sku) {
        return sku == null || sku.isBlank() ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    private static FacetSeed facet(ProductFacetType type, String code, String label) {
        return new FacetSeed(type, code, label);
    }

    private static String facetKey(ProductFacetType type, String code) {
        return type.name() + ":" + code.toLowerCase(Locale.ROOT);
    }

    private record CategorySeed(String name, String slug, String description, String icon) {}
    private record BrandSeed(String name, String slug, String country) {}
    private record FacetSeed(ProductFacetType type, String code, String label) {}
    private record FacetRef(ProductFacetType type, String code) {}
    private record ProductSeed(String categorySlug, String brandSlug, String sku, String name,
                               String description, String benefits, String inci, String directions,
                               String spf, BigDecimal sizeValue, String sizeUnit, String variantLabel,
                               String shadeName, String shadeHex, BigDecimal price,
                               BigDecimal discountPrice, int stock, boolean featured,
                               List<FacetRef> facets) {}
}
