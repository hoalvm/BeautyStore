package t4m.beauty_store.shipper.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ShipperConcurrencyIntegrationTests {
    @Autowired ShipperService shipperService;
    @Autowired OrderRepository orderRepository;
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private Long orderId;
    private Long firstShipperId;
    private Long secondShipperId;
    private String firstEmail;
    private String secondEmail;

    @BeforeEach
    void createCommittedFixture() {
        String suffix = UUID.randomUUID().toString();
        firstEmail = "race-first-" + suffix + "@beautystore.test";
        secondEmail = "race-second-" + suffix + "@beautystore.test";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User first = userRepository.save(shipper(firstEmail));
            User second = userRepository.save(shipper(secondEmail));
            firstShipperId = first.getId();
            secondShipperId = second.getId();
            orderId = orderRepository.save(processingOrder(suffix)).getId();
        });
    }

    @AfterEach
    void removeFixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (orderId != null) orderRepository.deleteById(orderId);
            if (firstShipperId != null) userRepository.deleteById(firstShipperId);
            if (secondShipperId != null) userRepository.deleteById(secondShipperId);
        });
    }

    @Test
    void concurrentAcceptanceHasExactlyOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstResult = executor.submit(() -> acceptWhenReleased(firstEmail, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> acceptWhenReleased(secondEmail, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = List.of(
                firstResult.get(10, TimeUnit.SECONDS),
                secondResult.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            Order persisted = new TransactionTemplate(transactionManager)
                .execute(status -> orderRepository.findById(orderId).orElseThrow());
            assertThat(persisted.getStatus()).isEqualTo(OrderStatus.SHIPPING);
            assertThat(persisted.getShipper().getId()).isIn(firstShipperId, secondShipperId);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean acceptWhenReleased(
            String email, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            shipperService.acceptOrder(orderId, email);
            return true;
        } catch (IllegalArgumentException rejectedAfterLock) {
            return false;
        }
    }

    private static User shipper(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswd("unused-test-password");
        user.setName("Concurrency shipper");
        user.setActivated(true);
        return user;
    }

    private static Order processingOrder(String suffix) {
        return Order.builder()
            .orderNumber("ORD-RACE-" + suffix)
            .customerName("Concurrency customer")
            .customerEmail("customer-" + suffix + "@beautystore.test")
            .customerPhone("0900000000")
            .shippingAddress("Ho Chi Minh City")
            .paymentMethod("COD")
            .paymentStatus("COD_PENDING")
            .totalAmount(BigDecimal.valueOf(100_000))
            .inventoryCommitted(true)
            .status(OrderStatus.PROCESSING)
            .build();
    }
}
