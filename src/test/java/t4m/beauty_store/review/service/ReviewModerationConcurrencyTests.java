package t4m.beauty_store.review.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.review.entity.Review;
import t4m.beauty_store.review.entity.ReviewStatus;
import t4m.beauty_store.review.repository.ReviewRepository;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewModerationConcurrencyTests {
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;

    @Test
    void simultaneousApprovalsAndRejectionKeepAggregateAccurate() throws Exception {
        Product product = products.saveAndFlush(Product.builder().name("Review fixture")
                .slug("review-fixture-" + UUID.randomUUID()).build());
        long first = review(product, 5);
        long second = review(product, 3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> approve(first, ready, start));
            var two = executor.submit(() -> approve(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }
        Product approved = products.findById(product.getId()).orElseThrow();
        assertThat(approved.getRatingCount()).isEqualTo(2);
        assertThat(approved.getAverageRating()).isEqualTo(4.0);
        service.moderate(second, ReviewStatus.REJECTED);
        Product corrected = products.findById(product.getId()).orElseThrow();
        assertThat(corrected.getRatingCount()).isEqualTo(1);
        assertThat(corrected.getAverageRating()).isEqualTo(5.0);
    }

    private void approve(long id, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent start timed out");
            service.moderate(id, ReviewStatus.APPROVED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private long review(Product product, int stars) {
        Order order = Order.builder().orderNumber("REVIEW-" + UUID.randomUUID())
                .customerName("Review fixture").customerEmail("review@example.test")
                .customerPhone("0901234567").shippingAddress("Demo")
                .paymentMethod("COD").totalAmount(BigDecimal.ONE).status(OrderStatus.DELIVERED).build();
        OrderItem item = OrderItem.builder().order(order).product(product)
                .productName(product.getName()).quantity(1).price(BigDecimal.ONE).build();
        order.getOrderItems().add(item);
        orders.saveAndFlush(order);
        return reviews.saveAndFlush(Review.builder().product(product).orderItem(item)
                .stars(stars).title("Concurrency fixture").build()).getId();
    }
}
