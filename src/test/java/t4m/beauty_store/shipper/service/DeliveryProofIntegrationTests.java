package t4m.beauty_store.shipper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.entity.*;
import t4m.beauty_store.order.repository.*;
import t4m.beauty_store.shipper.repository.DeliveryProofRepository;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real locks, image decoding, storage and HTTP authorization; no enclosing test transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryProofIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ShipperService service;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired DeliveryProofRepository proofs;
    @Autowired GuestOrderAccessRepository guestAccess;
    @Autowired PlatformTransactionManager transactions;
    private Long orderId;
    private Long guestAccessId;
    private User owner;
    private User shipper;
    private User other;
    private TransactionTemplate tx;

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(transactions);
        owner = account("owner");
        shipper = account("shipper");
        other = account("other");
        orderId = orders.saveAndFlush(Order.builder().user(owner).shipper(shipper)
            .orderNumber(("PROOF-" + UUID.randomUUID()).toUpperCase(Locale.ROOT))
            .customerName("Proof test").customerEmail(owner.getEmail()).customerPhone("0900000000")
            .shippingAddress("Test address").paymentMethod("COD").paymentStatus("COD_PENDING")
            .status(OrderStatus.SHIPPING).inventoryCommitted(true).totalAmount(BigDecimal.valueOf(100000)).build()).getId();
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(status -> {
            var order = orders.findById(orderId).orElseThrow();
            Long proofId = order.getDeliveryProof() == null ? null : order.getDeliveryProof().getId();
            if (guestAccessId != null) guestAccess.deleteById(guestAccessId);
            orders.delete(order);
            orders.flush();
            if (proofId != null) proofs.deleteById(proofId);
            proofs.flush();
            users.deleteAllById(List.of(owner.getId(), shipper.getId(), other.getId()));
        });
    }

    @Test
    void multipartDeliveryStoresPrivateJpegAndPreservesCommerceState() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, completeUrl()).file(photo())
            .with(user(shipper.getEmail()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.paymentStatus").value("COD_PAID"))
            .andExpect(jsonPath("$.hasDeliveryProof").value(true))
            .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
            .andExpect(jsonPath("$.imageBytes").doesNotExist());
        for (var identity : List.of(user(owner.getEmail()).roles("USER"),
                user(shipper.getEmail()).roles("SHIPPER"), user("admin@test.vn").roles("ADMIN"))) {
            mvc.perform(get(proofUrl()).with(identity)).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        mvc.perform(get(proofUrl())).andExpect(status().isForbidden());
        mvc.perform(get(proofUrl()).with(user(other.getEmail()).roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get(proofUrl()).with(user(other.getEmail()).roles("SHIPPER")))
            .andExpect(status().isForbidden());
        tx.executeWithoutResult(status -> {
            var order = orders.findById(orderId).orElseThrow();
            assertThat(order.getInventoryCommitted()).isTrue();
            assertThat(order.getDeliveryProof().getUploadedBy()).isEqualTo(shipper.getId());
            assertThat(order.getDeliveryProof().getImageBytes()[0] & 0xff).isEqualTo(255);
        });
    }

    @Test
    void missingFakeOversizedAndUnauthorizedImagesDoNotCompleteOrder() throws Exception {
        long before = proofs.count();
        mvc.perform(put(completeUrl()).with(user(shipper.getEmail()).roles("SHIPPER")))
            .andExpect(status().isBadRequest());
        for (var bad : List.of(
                new MockMultipartFile("image", "fake.png", "image/png", "<svg/>".getBytes()),
                new MockMultipartFile("image", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))) {
            mvc.perform(multipart(HttpMethod.PUT, completeUrl()).file(bad)
                .with(user(shipper.getEmail()).roles("SHIPPER"))).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart(HttpMethod.PUT, completeUrl()).file(photo())
            .with(user(other.getEmail()).roles("SHIPPER"))).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, completeUrl()).file(photo())
            .with(user(owner.getEmail()).roles("USER"))).andExpect(status().isForbidden());
        assertShippingUnchanged();
        assertThat(proofs.count()).isEqualTo(before);
    }

    @Test
    void concurrentRetriesKeepOnePhotoOneTimestampAndOnePaymentTransition() throws Exception {
        long before = proofs.count();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Order> work = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return service.completeDelivery(orderId, shipper.getEmail(), photo());
            };
            var first = executor.submit(work);
            var second = executor.submit(work);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Order a = first.get(15, TimeUnit.SECONDS);
            Order b = second.get(15, TimeUnit.SECONDS);
            assertThat(a.getDeliveryProof().getId()).isEqualTo(b.getDeliveryProof().getId());
            assertThat(a.getDeliveredAt()).isEqualTo(b.getDeliveredAt());
            assertThat(b.getPaymentStatus()).isEqualTo("COD_PAID");
            assertThat(proofs.count()).isEqualTo(before + 1);
            mvc.perform(put(completeUrl()).with(user(other.getEmail()).roles("SHIPPER")))
                .andExpect(status().isForbidden());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void outerTransactionFailureRollsBackBothProofAndDelivery() {
        long before = proofs.count();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            try {
                service.completeDelivery(orderId, shipper.getEmail(), photo());
                orders.flush();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            throw new IllegalStateException("Simulated DB workflow rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertShippingUnchanged();
        assertThat(proofs.count()).isEqualTo(before);
    }

    @Test
    void guestNeedsVerifiedUnexpiredOrderScopedTokenEvenWhenLoggedInAsSomeoneElse() throws Exception {
        String token = "a".repeat(43);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes()));
        tx.executeWithoutResult(status -> {
            Order order = orders.findById(orderId).orElseThrow();
            order.setUser(null);
            var now = StoreTime.now();
            guestAccessId = guestAccess.save(GuestOrderAccess.builder().order(order).email(owner.getEmail())
                .otpHash("not-used").expiresAt(now.plusMinutes(5)).resendAvailableAt(now.plusMinutes(1))
                .verifiedAt(now).accessTokenHash(hash).accessTokenExpiresAt(now.plusMinutes(30)).build()).getId();
        });
        service.completeDelivery(orderId, shipper.getEmail(), photo());
        mvc.perform(get(proofUrl()).header("X-Order-Token", token)).andExpect(status().isOk());
        mvc.perform(get(proofUrl()).header("X-Order-Token", token)
            .with(user(other.getEmail()).roles("USER"))).andExpect(status().isOk());
        mvc.perform(get(proofUrl()).header("X-Order-Token", "b".repeat(43))).andExpect(status().isForbidden());
        tx.executeWithoutResult(status -> guestAccess.findById(guestAccessId).orElseThrow()
            .setAccessTokenExpiresAt(StoreTime.now().minusMinutes(1)));
        mvc.perform(get(proofUrl()).header("X-Order-Token", token)).andExpect(status().isForbidden());
    }

    @Test
    void vnpayPaymentIsNotRewrittenAndLegacyDeliveryCannotAcquireNewEvidence() throws Exception {
        tx.executeWithoutResult(status -> {
            var order = orders.findById(orderId).orElseThrow();
            order.setPaymentMethod("VNPAY");
            order.setPaymentStatus("PAID");
        });
        assertThat(service.completeDelivery(orderId, shipper.getEmail(), photo()).getPaymentStatus()).isEqualTo("PAID");
        tx.executeWithoutResult(status -> {
            var order = orders.findById(orderId).orElseThrow();
            var proof = order.getDeliveryProof();
            order.setDeliveryProof(null);
            orders.flush();
            proofs.delete(proof);
        });
        mvc.perform(get(proofUrl()).with(user(owner.getEmail()).roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(multipart(HttpMethod.PUT, completeUrl()).file(photo())
            .with(user(shipper.getEmail()).roles("SHIPPER"))).andExpect(status().isConflict());
    }

    private void assertShippingUnchanged() {
        var order = orders.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getPaymentStatus()).isEqualTo("COD_PENDING");
        assertThat(order.getDeliveryProof()).isNull();
        assertThat(order.getDeliveredAt()).isNull();
    }

    private User account(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@proof.test");
        user.setPasswd("unused");
        user.setActivated(true);
        return users.saveAndFlush(user);
    }

    private MockMultipartFile photo() throws Exception { return DeliveryProofImageProcessorTests.png(8, 4); }
    private String completeUrl() { return "/api/shipper/orders/" + orderId + "/complete"; }
    private String proofUrl() { return "/api/delivery-proofs/" + orderId; }
}
