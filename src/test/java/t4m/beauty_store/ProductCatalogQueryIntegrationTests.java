package t4m.beauty_store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.product.dto.ProductResponse;
import t4m.beauty_store.product.entity.Category;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.CategoryRepository;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;
import t4m.beauty_store.product.service.ProductService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductCatalogQueryIntegrationTests {
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired InventoryBatchRepository batchRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void brandFilterMatchesOnlyTheExactSlugOrNameIgnoringCase() {
        assertThat(filter(null, "Aurora", null, null).getTotalElements()).isZero();

        Page<Product> byName = filter(null, "AURORA BOTANICS", null, null);
        Page<Product> bySlug = filter(null, "AURORA-BOTANICS", null, null);

        assertThat(byName.getContent()).isNotEmpty()
            .allSatisfy(product -> assertThat(product.getBrandEntity().getName())
                .isEqualTo("Aurora Botanics"));
        assertThat(bySlug.getTotalElements()).isEqualTo(byName.getTotalElements());
    }

    @Test
    void hugePageOffsetReturnsAnEmptyPageWithoutIntegerOverflow() {
        Page<Product> result = productService.getAllProducts(PageRequest.of(Integer.MAX_VALUE, 100));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(40);
    }

    @Test
    void keywordSearchStillIncludesInciFacetAndVariantSku() {
        Product product = productRepository.findAllByActiveTrue().getFirst();
        product.setInci("Aqua, CatalogNeedle-Unique, Glycerin, 100% literal");
        productRepository.saveAndFlush(product);
        ProductVariant variant = variantRepository
            .findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(product.getId()).getFirst();

        assertThat(productService.searchProducts("catalogneedle-unique", PageRequest.of(0, 20)).getContent())
            .extracting(Product::getId).contains(product.getId());
        assertThat(productService.searchProducts(variant.getSku().toLowerCase(), PageRequest.of(0, 20)).getContent())
            .extracting(Product::getId).contains(product.getId());
        assertThat(productService.searchProducts("all-skin", PageRequest.of(0, 50)).getContent())
            .isNotEmpty();
        assertThat(productService.searchProducts("100% literal", PageRequest.of(0, 50)).getContent())
            .extracting(Product::getId).containsExactly(product.getId());
    }

    @Test
    void categoryFilterIncludesActiveDescendantsAndRatingSortRunsInTheDatabase() {
        Category parent = categoryRepository.save(Category.builder()
            .name("Catalog parent category").slug("catalog-parent-category").active(true).build());
        Category child = categoryRepository.save(Category.builder()
            .name("Catalog child category").slug("catalog-child-category")
            .parent(parent).active(true).build());
        Product childProduct = productRepository.save(Product.builder()
            .name("Catalog child product").slug("catalog-child-product")
            .category(child).averageRating(5.0).active(true).build());
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
            .product(childProduct).sku("CATALOG-CHILD-PRODUCT").price(BigDecimal.TEN)
            .defaultVariant(true).active(true).build());
        InventoryBatch batch = batchRepository.save(InventoryBatch.builder()
            .variant(variant).batchCode("CATALOG-CHILD-BATCH")
            .manufacturedDate(LocalDate.now().minusMonths(1))
            .expiryDate(LocalDate.now().plusYears(1))
            .quantityOnHand(10).quantityReserved(0).active(true).build());
        variant.getBatches().add(batch);
        childProduct.getVariants().add(variant);
        entityManager.flush();

        ProductService.ProductFilter categoryFilter = new ProductService.ProductFilter(
            null, null, parent.getSlug(), null, null, null, null, null, null, null, null,
            null, null, null, null, "newest");
        assertThat(productService.filterProducts(categoryFilter, PageRequest.of(0, 20)).getContent())
            .extracting(Product::getId).contains(childProduct.getId());

        ProductService.ProductFilter ratingFilter = new ProductService.ProductFilter(
            null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, "rating-desc");
        assertThat(productService.filterProducts(ratingFilter, PageRequest.of(0, 1)).getContent())
            .extracting(Product::getId).containsExactly(childProduct.getId());
    }

    @Test
    void inactiveAncestorAndExpiredOnlyInventoryKeepProductsOutOfSellableResults() {
        Category hiddenParent = categoryRepository.save(Category.builder()
            .name("Hidden catalog parent").slug("hidden-catalog-parent").active(false).build());
        Category activeChild = categoryRepository.save(Category.builder()
            .name("Active child of hidden parent").slug("active-child-hidden-parent")
            .parent(hiddenParent).active(true).build());
        Product hiddenByTree = productRepository.save(Product.builder()
            .name("Catalog hidden by parent").slug("catalog-hidden-by-parent")
            .category(activeChild).active(true).build());
        ProductVariant hiddenVariant = variantRepository.save(ProductVariant.builder()
            .product(hiddenByTree).sku("CATALOG-HIDDEN-TREE").price(BigDecimal.TEN)
            .defaultVariant(true).active(true).build());
        hiddenByTree.getVariants().add(hiddenVariant);

        Product expiredOnly = productRepository.save(Product.builder()
            .name("Catalog expired batch only").slug("catalog-expired-batch-only")
            .active(true).build());
        ProductVariant expiredVariant = variantRepository.save(ProductVariant.builder()
            .product(expiredOnly).sku("CATALOG-EXPIRED-ONLY").price(BigDecimal.TEN)
            .defaultVariant(true).active(true).build());
        InventoryBatch expiredBatch = batchRepository.save(InventoryBatch.builder()
            .variant(expiredVariant).batchCode("CATALOG-EXPIRED-BATCH")
            .manufacturedDate(LocalDate.now().minusYears(2))
            .expiryDate(LocalDate.now().minusDays(1))
            .quantityOnHand(20).quantityReserved(0).active(true).build());
        expiredVariant.getBatches().add(expiredBatch);
        entityManager.flush();

        Page<Product> publicPage = productService.getAllProducts(PageRequest.of(0, 100));
        assertThat(publicPage.getContent()).extracting(Product::getId)
            .doesNotContain(hiddenByTree.getId());
        assertThat(filter("catalog-expired-batch-only", null, true, null).getContent()).isEmpty();
        assertThat(filter("catalog-expired-batch-only", null, false, null).getContent())
            .extracting(Product::getId).containsExactly(expiredOnly.getId());
    }

    @Test
    void queryCountForAFullPageDoesNotGrowPerProductOrVariant() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long oneProductQueries = queryCountForPage(statistics, 1);
            long twelveProductQueries = queryCountForPage(statistics, 12);

            assertThat(twelveProductQueries).isLessThanOrEqualTo(oneProductQueries + 2);
        } finally {
            statistics.clear();
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private long queryCountForPage(Statistics statistics, int size) {
        entityManager.clear();
        statistics.clear();
        Page<Product> result = productService.getAllProducts(PageRequest.of(0, size));
        List<ProductResponse> ignored = result.getContent().stream().map(ProductResponse::fromEntity).toList();
        assertThat(ignored).hasSize(size);
        return statistics.getPrepareStatementCount();
    }

    private Page<Product> filter(String keyword, String brand, Boolean inStock, Boolean onSale) {
        return productService.filterProducts(new ProductService.ProductFilter(
            keyword, null, null, brand, null, null, null, null, null, null, null,
            null, null, inStock, onSale, "newest"), PageRequest.of(0, 100));
    }
}
