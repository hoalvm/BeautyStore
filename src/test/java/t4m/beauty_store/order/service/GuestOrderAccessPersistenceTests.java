package t4m.beauty_store.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import t4m.beauty_store.auth.exception.OtpRateLimitException;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;
import t4m.beauty_store.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GuestOrderAccessPersistenceTests {
    @Autowired
    private GuestOrderAccessService service;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private GuestOrderAccessRepository accessRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void failedAttemptsCommitEvenThoughPublicServiceThrows() {
        String unique = Long.toUnsignedString(System.nanoTime());
        String email = "guest-" + unique + "@example.com";
        Order order = orderRepository.saveAndFlush(Order.builder()
            .orderNumber("ORD-OTP-" + unique)
            .customerName("Khách OTP")
            .customerEmail(email)
            .customerPhone("0900000000")
            .shippingAddress("Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
            .paymentMethod("COD")
            .totalAmount(BigDecimal.valueOf(100_000))
            .status(OrderStatus.PENDING)
            .build());
        GuestOrderAccess challenge = accessRepository.saveAndFlush(GuestOrderAccess.builder()
            .order(order)
            .email(email)
            .otpHash(passwordEncoder.encode("123456"))
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .resendAvailableAt(LocalDateTime.now().plusSeconds(60))
            .build());

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> service.verifyOtp(
                order.getOrderNumber(), email, "000000", "198.51.100.25"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(accessRepository.findById(challenge.getId()).orElseThrow().getAttempts())
                .isEqualTo(attempt);
        }

        assertThatThrownBy(() -> service.verifyOtp(
            order.getOrderNumber(), email, "000000", "198.51.100.25"))
            .isInstanceOf(OtpRateLimitException.class);
        assertThat(accessRepository.findById(challenge.getId()).orElseThrow().getAttempts())
            .isEqualTo(5);

        assertThatThrownBy(() -> service.verifyOtp(
            order.getOrderNumber(), email, "123456", "198.51.100.25"))
            .isInstanceOf(OtpRateLimitException.class);
        assertThat(accessRepository.findById(challenge.getId()).orElseThrow().getAttempts())
            .isEqualTo(5);
    }
}
