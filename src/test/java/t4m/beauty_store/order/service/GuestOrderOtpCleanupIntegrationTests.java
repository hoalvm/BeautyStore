package t4m.beauty_store.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;
import t4m.beauty_store.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GuestOrderOtpCleanupIntegrationTests {
    @Autowired GuestOrderAccessRepository accessRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void immediateFailureAfterCommitReallyDeletesPersistedChallenge() {
        EmailService email = mock(EmailService.class);
        when(email.sendGuestOrderOtp(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("mail rejected")));
        GuestOrderOtpDeliveryService delivery = new GuestOrderOtpDeliveryService(email, accessRepository);
        Order order = orderRepository.saveAndFlush(Order.builder()
            .customerName("OTP cleanup fixture")
            .customerEmail("otp-cleanup@example.test")
            .customerPhone("0900000000")
            .shippingAddress("Test address")
            .paymentMethod("COD")
            .totalAmount(BigDecimal.valueOf(100000))
            .status(OrderStatus.PENDING)
            .build());
        try {
            Long challengeId = new TransactionTemplate(transactionManager).execute(status -> {
                GuestOrderAccess challenge = accessRepository.saveAndFlush(GuestOrderAccess.builder()
                    .order(order)
                    .email(order.getCustomerEmail())
                    .otpHash("not-a-real-otp-hash")
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .resendAvailableAt(LocalDateTime.now().plusMinutes(1))
                    .build());
                delivery.schedule(challenge.getId(), order.getCustomerEmail(),
                    order.getCustomerName(), order.getOrderNumber(), "123456");
                assertThat(accessRepository.existsById(challenge.getId())).isTrue();
                return challenge.getId();
            });

            assertThat(accessRepository.existsById(challengeId)).isFalse();
        } finally {
            accessRepository.findTopByOrderIdAndEmailIgnoreCaseOrderByCreatedAtDesc(
                order.getId(), order.getCustomerEmail()).ifPresent(accessRepository::delete);
            orderRepository.deleteById(order.getId());
        }
    }
}
