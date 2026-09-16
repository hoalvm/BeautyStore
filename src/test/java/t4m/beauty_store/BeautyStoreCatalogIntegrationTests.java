package t4m.beauty_store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.InventoryBatchRequest;
import t4m.beauty_store.admin.dto.ProductUpdateRequest;
import t4m.beauty_store.admin.dto.ProductVariantRequest;
import t4m.beauty_store.config.BeautyStoreCatalogSeeder;
import t4m.beauty_store.product.entity.*;
import t4m.beauty_store.product.dto.ProductResponse;
import t4m.beauty_store.product.repository.*;
import t4m.beauty_store.product.service.InventoryService;
import t4m.beauty_store.product.service.ProductService;
import t4m.beauty_store.product.service.ProductVariantService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BeautyStoreCatalogIntegrationTests {
    private static final Set<String> CATEGORY_NAMES = Set.of(
        "Chăm sóc da mặt", "Chống nắng", "Trang điểm", "Chăm sóc cơ thể",
        "Chăm sóc tóc", "Nước hoa", "Dụng cụ & phụ kiện", "Bộ quà tặng & minisize"
    );

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired InventoryBatchRepository batchRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired ProductService productService;
    @Autowired ProductVariantService variantService;
    @Autowired InventoryService inventoryService;
    @Autowired BeautyStoreCatalogSeeder seeder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void seedsEightBeautyCategoriesAndFortySellableProducts() {
        List<Category> categories = categoryRepository.findByActiveTrue();
        assertThat(categories).hasSize(8);
        assertThat(categories).extracting(Category::getName).containsExactlyInAnyOrderElementsOf(CATEGORY_NAMES);

        List<ProductVariant> variants = variantRepository.findAll().stream()
            .filter(variant -> variant.getSku().startsWith("BEA-DEMO-"))
            .toList();
        assertThat(variants).hasSize(40).allSatisfy(variant -> {
            assertThat(variant.getProduct().getBrandEntity()).isNotNull();
            assertThat(variant.getProduct().getCategory()).isNotNull();
            assertThat(variant.getDefaultVariant()).isTrue();
            assertThat(variant.getAvailableStock()).isPositive();
        });
        assertThat(batchRepository.findAll()).hasSize(40);
    }

    @Test
    void seederArchivesLegacyCatalogWithoutRestoringAdminHiddenDemoProduct() throws Exception {
        Category legacy = categoryRepository.save(Category.builder()
            .name("Đồ chơi giáo dục").slug("legacy-toy-category").active(true).build());
        Product oldProduct = productRepository.save(Product.builder()
            .name("Đồ chơi cũ").slug("do-choi-cu")
            .category(legacy).active(true).build());
        ProductVariant legacyVariant = variantRepository.save(ProductVariant.builder()
            .product(oldProduct).sku("EDU-OLD-001").price(BigDecimal.TEN)
            .defaultVariant(true).active(true).build());
        oldProduct.getVariants().add(legacyVariant);
        Product hiddenDemo = variantRepository.findAll().stream()
            .filter(variant -> "BEA-DEMO-SKIN-001".equals(variant.getSku()))
            .map(ProductVariant::getProduct).findFirst().orElseThrow();
        hiddenDemo.setActive(false);
        productRepository.saveAndFlush(hiddenDemo);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(categoryRepository.findById(legacy.getId()).orElseThrow().getActive()).isFalse();
        assertThat(productRepository.findById(oldProduct.getId()).orElseThrow().getActive()).isFalse();
        assertThat(productRepository.findById(hiddenDemo.getId()).orElseThrow().getActive()).isFalse();
        assertThat(variantRepository.findAll().stream()
            .filter(variant -> variant.getSku().startsWith("BEA-DEMO-")).count()).isEqualTo(40);
    }

    @Test
    void publicFilterCombinesCategoryBrandFacetPriceSaleAndStock() {
        ProductService.ProductFilter filter = new ProductService.ProductFilter(
            null, null, "cham-soc-da-mat", "aurora-botanics", "all-skin", "hydration",
            null, null, null, null, null, BigDecimal.ZERO, new BigDecimal("1000000"),
            true, false, "price-asc");

        Page<Product> result = productService.filterProducts(filter, PageRequest.of(0, 20));

        assertThat(result.getContent()).isNotEmpty().allSatisfy(product -> {
            assertThat(product.getCategory().getSlug()).isEqualTo("cham-soc-da-mat");
            assertThat(product.getBrandEntity().getSlug()).isEqualTo("aurora-botanics");
            assertThat(product.getFacets()).anyMatch(facet -> facet.getCode().equals("hydration"));
        });
    }

    @Test
    void validatesVariantSkuDiscountSizeAndDefaultInvariant() {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        ProductVariant existing = variantRepository.findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(
            product.getId()).getFirst();

        ProductVariantRequest duplicate = validVariant(existing.getSku().toLowerCase());
        assertThatThrownBy(() -> variantService.create(product.getId(), duplicate))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SKU");

        ProductVariantRequest invalidDiscount = validVariant("BEA-TEST-DISCOUNT");
        invalidDiscount.setDiscountPrice(invalidDiscount.getPrice());
        assertThatThrownBy(() -> variantService.create(product.getId(), invalidDiscount))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Discount");

        ProductVariantRequest missingUnit = validVariant("BEA-TEST-SIZE");
        missingUnit.setSizeValue(BigDecimal.TEN);
        missingUnit.setSizeUnit(null);
        assertThatThrownBy(() -> variantService.create(product.getId(), missingUnit))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provided together");

        assertThatThrownBy(() -> variantService.setActive(existing.getId(), false))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one active variant");
    }

    @Test
    void reservesInventoryInFefoOrderAndCanCommitExactlyOnce() {
        ProductVariant variant = variantRepository.findAll().stream()
            .filter(candidate -> "BEA-DEMO-SKIN-001".equals(candidate.getSku()))
            .findFirst().orElseThrow();
        InventoryBatchRequest nearExpiry = new InventoryBatchRequest();
        nearExpiry.setBatchCode("NEAR-EXPIRY");
        nearExpiry.setManufacturedDate(LocalDate.now().minusMonths(10));
        nearExpiry.setExpiryDate(LocalDate.now().plusMonths(1));
        nearExpiry.setQuantityOnHand(2);
        nearExpiry.setActive(true);
        InventoryBatch firstBatch = inventoryService.createBatch(variant.getId(), nearExpiry);

        List<InventoryService.BatchReservation> reservations = inventoryService.reserveFefo(variant.getId(), 3);

        assertThat(reservations).hasSize(2);
        assertThat(reservations.getFirst().batchId()).isEqualTo(firstBatch.getId());
        assertThat(reservations.getFirst().quantity()).isEqualTo(2);
        Map<Long, Integer> allocation = reservations.stream().collect(java.util.stream.Collectors.toMap(
            InventoryService.BatchReservation::batchId, InventoryService.BatchReservation::quantity));
        inventoryService.commitReservations(allocation);
        assertThat(batchRepository.findById(firstBatch.getId()).orElseThrow().getQuantityOnHand()).isZero();
        assertThatThrownBy(() -> inventoryService.commitReservations(allocation))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
    }

    @Test
    void rejectsExpiredNewBatchAndInvalidManufacturedDate() {
        Long variantId = variantRepository.findAll().getFirst().getId();
        InventoryBatchRequest request = new InventoryBatchRequest();
        request.setBatchCode("EXPIRED");
        request.setManufacturedDate(LocalDate.now());
        request.setExpiryDate(LocalDate.now().minusDays(1));
        request.setQuantityOnHand(5);

        assertThatThrownBy(() -> inventoryService.createBatch(variantId, request))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expired");
    }

    @Test
    void archivingDefaultVariantPromotesExactlyOneActiveDefault() {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        ProductVariant original = variantRepository
            .findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(product.getId()).getFirst();
        ProductVariantRequest secondRequest = validVariant("BEA-TEST-SECOND-DEFAULT");
        secondRequest.setDefaultVariant(false);
        ProductVariant second = variantService.create(product.getId(), secondRequest);

        variantService.setActive(original.getId(), false);

        List<ProductVariant> all = variantRepository
            .findByProductIdOrderByDefaultVariantDescIdAsc(product.getId());
        assertThat(all).filteredOn(variant -> Boolean.TRUE.equals(variant.getDefaultVariant()))
            .containsExactly(second);
        assertThat(variantRepository.findById(original.getId()).orElseThrow().getDefaultVariant()).isFalse();
    }

    @Test
    void inactiveVariantCannotDisplaceTheActiveDefault() {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        ProductVariant original = variantRepository
            .findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(product.getId()).getFirst();
        ProductVariantRequest inactive = validVariant("BEA-TEST-INACTIVE-DEFAULT");
        inactive.setDefaultVariant(true);
        inactive.setActive(false);

        ProductVariant created = variantService.create(product.getId(), inactive);

        assertThat(created.getDefaultVariant()).isFalse();
        assertThat(variantRepository.findById(original.getId()).orElseThrow().getDefaultVariant()).isTrue();
    }

    @Test
    void adminCanExplicitlyClearNullableProductMetadata() throws Exception {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        product.setBenefits("Old benefits");
        product.setInci("Old INCI");
        product.setSpf("SPF 50");
        product.setPaoMonths(12);
        product.setShelfLifeMonths(36);
        product.setWarrantyMonths(6);
        product.setSpecifications("Old specifications");
        productRepository.saveAndFlush(product);

        ProductUpdateRequest request = objectMapper.readValue("""
            {"benefits":null,"inci":null,"spf":null,"paoMonths":null,
             "shelfLifeMonths":null,"warrantyMonths":null,"specifications":null,
             "brandId":null,"facetIds":[]}
            """, ProductUpdateRequest.class);
        Product updated = productService.updateProduct(product.getId(), request);

        assertThat(updated.getBenefits()).isNull();
        assertThat(updated.getInci()).isNull();
        assertThat(updated.getSpf()).isNull();
        assertThat(updated.getPaoMonths()).isNull();
        assertThat(updated.getShelfLifeMonths()).isNull();
        assertThat(updated.getWarrantyMonths()).isNull();
        assertThat(updated.getSpecifications()).isNull();
        assertThat(updated.getBrandEntity()).isNull();
        assertThat(updated.getFacets()).isEmpty();
    }

    @Test
    void publicResponseDoesNotExposeImagesFromInactiveVariants() {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        ProductVariantRequest inactiveRequest = validVariant("BEA-TEST-INACTIVE-IMAGE");
        inactiveRequest.setActive(false);
        ProductVariant inactive = variantService.create(product.getId(), inactiveRequest);
        ProductImage archivedImage = imageRepository.save(ProductImage.builder()
            .product(product)
            .variant(inactive)
            .url("https://example.invalid/archived-variant.jpg")
            .altText("Archived variant")
            .sortOrder(0)
            .primary(true)
            .build());
        product.getImages().add(archivedImage);

        ProductResponse response = ProductResponse.fromEntity(product);

        assertThat(response.getVariants()).noneMatch(variant -> variant.getId().equals(inactive.getId()));
        assertThat(response.getGallery()).noneMatch(image -> image.getId().equals(archivedImage.getId()));
    }

    private static ProductVariantRequest validVariant(String sku) {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku(sku);
        request.setPrice(new BigDecimal("200000"));
        request.setDiscountPrice(new BigDecimal("180000"));
        request.setLowStockThreshold(10);
        request.setActive(true);
        return request;
    }
}
