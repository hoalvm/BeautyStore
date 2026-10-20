package t4m.beauty_store.product.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import t4m.beauty_store.admin.dto.ProductImageRequest;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductImageRepository;
import t4m.beauty_store.product.repository.ProductRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductImageConcurrencyIntegrationTests {
    @Autowired ProductImageService images;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository imageRepository;
    private Long productId;

    @BeforeEach
    void createProduct() {
        productId = products.saveAndFlush(Product.builder()
            .name("Gallery concurrency fixture")
            .slug("gallery-fixture-" + UUID.randomUUID())
            .build()).getId();
    }

    @AfterEach
    void removeProduct() {
        products.deleteById(productId);
    }

    @Test
    void twoConcurrentUploadsCannotExceedEightGalleryImages() throws Exception {
        for (int index = 0; index < 7; index++) images.create(productId, request(false));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> addWhenReleased(ready, start));
            var second = executor.submit(() -> addWhenReleased(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(imageRepository.countByProductIdAndVariantIsNull(productId)).isEqualTo(8);
        } finally {
            start.countDown();
        }
    }

    @Test
    void competingPrimarySelectionsLeaveExactlyOnePrimary() throws Exception {
        Long firstImage = images.create(productId, request(false)).getId();
        Long secondImage = images.create(productId, request(false)).getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> primaryWhenReleased(firstImage, ready, start));
            var second = executor.submit(() -> primaryWhenReleased(secondImage, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId))
                .filteredOn(image -> Boolean.TRUE.equals(image.getPrimary())).hasSize(1);
        } finally {
            start.countDown();
        }
    }

    @Test
    void deletingPrimaryRemovesItAndPromotesRemainingImage() {
        Long first = images.create(productId, request(true)).getId();
        Long second = images.create(productId, request(false)).getId();

        images.delete(productId, first);

        assertThat(imageRepository.existsById(first)).isFalse();
        assertThat(imageRepository.findById(second).orElseThrow().getPrimary()).isTrue();
        assertThat(imageRepository.countByProductIdAndVariantIsNull(productId)).isEqualTo(1);
    }

    @Test
    void imageMutationCannotTargetAnotherProductsGallery() {
        Long image = images.create(productId, request(true)).getId();
        Product other = products.saveAndFlush(Product.builder()
            .name("Other gallery fixture").slug("other-gallery-" + UUID.randomUUID()).build());
        try {
            assertThatThrownBy(() -> images.delete(other.getId(), image))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("belong");
            assertThatThrownBy(() -> images.setPrimary(other.getId(), image))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("belong");
            assertThatThrownBy(() -> images.update(other.getId(), image, request(false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("belong");
            assertThat(imageRepository.existsById(image)).isTrue();
        } finally {
            products.deleteById(other.getId());
        }
    }

    private boolean addWhenReleased(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        try {
            images.create(productId, request(false));
            return true;
        } catch (IllegalArgumentException limit) {
            assertThat(limit).hasMessageContaining("at most 8");
            return false;
        }
    }

    private void primaryWhenReleased(Long imageId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            images.setPrimary(productId, imageId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static ProductImageRequest request(boolean primary) {
        ProductImageRequest request = new ProductImageRequest();
        request.setUrl("/images/beauty/demo.svg");
        request.setAltText("Gallery test image");
        request.setPrimary(primary);
        return request;
    }
}
