package t4m.beauty_store.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import t4m.beauty_store.auth.exception.OtpRateLimitException;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;
import t4m.beauty_store.order.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestOrderAccessServiceTests {
    private OrderRepository orderRepository;
    private GuestOrderAccessRepository accessRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private GuestOrderOtpVerificationService verificationService;
    private GuestOrderOtpRateLimiter rateLimiter;
    private GuestOrderAccessService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        accessRepository = mock(GuestOrderAccessRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        verificationService = mock(GuestOrderOtpVerificationService.class);
        rateLimiter = mock(GuestOrderOtpRateLimiter.class);
        service = new GuestOrderAccessService(
            orderRepository, accessRepository, passwordEncoder, emailService,
            verificationService, rateLimiter);
    }

    @Test
    void issuedChallengeExpiresInFiveMinutesAndResendsAfterSixtySeconds() {
        Order order = guestOrder();
        when(orderRepository.findByOrderNumberForUpdate("ORD-GUEST-1"))
            .thenReturn(Optional.of(order));
        when(accessRepository.findTopByOrderIdAndEmailIgnoreCaseOrderByCreatedAtDesc(
            7L, "guest@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn("otp-hash");
        LocalDateTime before = LocalDateTime.now();

        service.requestOtp(" ord-guest-1 ", " GUEST@example.com ", "203.0.113.9");

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<GuestOrderAccess> accessCaptor =
            ArgumentCaptor.forClass(GuestOrderAccess.class);
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(accessRepository).save(accessCaptor.capture());
        verify(emailService).sendGuestOrderOtp(
            org.mockito.ArgumentMatchers.eq("guest@example.com"),
            org.mockito.ArgumentMatchers.eq("Khách Beauty"),
            org.mockito.ArgumentMatchers.eq("ORD-GUEST-1"),
            otpCaptor.capture());
        verify(rateLimiter).checkOtpRequest("guest@example.com", "203.0.113.9");

        GuestOrderAccess access = accessCaptor.getValue();
        assertThat(otpCaptor.getValue()).matches("\\d{6}");
        assertThat(access.getExpiresAt())
            .isBetween(before.plusMinutes(5), after.plusMinutes(5));
        assertThat(access.getResendAvailableAt())
            .isBetween(before.plusSeconds(60), after.plusSeconds(60));
    }

    @Test
    void resendDuringCooldownDoesNotCreateOrEmailAnotherChallenge() {
        Order order = guestOrder();
        GuestOrderAccess existing = GuestOrderAccess.builder()
            .resendAvailableAt(LocalDateTime.now().plusSeconds(30))
            .build();
        when(orderRepository.findByOrderNumberForUpdate("ORD-GUEST-1"))
            .thenReturn(Optional.of(order));
        when(accessRepository.findTopByOrderIdAndEmailIgnoreCaseOrderByCreatedAtDesc(
            7L, "guest@example.com")).thenReturn(Optional.of(existing));

        service.requestOtp("ORD-GUEST-1", "guest@example.com", "203.0.113.9");

        verify(accessRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailService, never()).sendGuestOrderOtp(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void invalidResultIsReportedOnlyAfterVerificationWriterReturns() {
        when(orderRepository.findByOrderNumber("ORD-GUEST-1"))
            .thenReturn(Optional.of(guestOrder()));
        when(verificationService.verifyLatest(7L, "guest@example.com", "000000"))
            .thenReturn(new GuestOrderOtpVerificationService.VerificationResult(
                GuestOrderOtpVerificationService.VerificationStatus.INVALID, null));

        assertThatThrownBy(() -> service.verifyOtp(
            "ORD-GUEST-1", "guest@example.com", "000000", "192.0.2.10"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(rateLimiter).checkOtpVerification("guest@example.com", "192.0.2.10");
        verify(verificationService).verifyLatest(7L, "guest@example.com", "000000");
    }

    @Test
    void fifthFailureIsReportedAsRateLimit() {
        when(orderRepository.findByOrderNumber("ORD-GUEST-1"))
            .thenReturn(Optional.of(guestOrder()));
        when(verificationService.verifyLatest(7L, "guest@example.com", "000000"))
            .thenReturn(new GuestOrderOtpVerificationService.VerificationResult(
                GuestOrderOtpVerificationService.VerificationStatus.LOCKED, null));

        assertThatThrownBy(() -> service.verifyOtp(
            "ORD-GUEST-1", "guest@example.com", "000000", "192.0.2.10"))
            .isInstanceOf(OtpRateLimitException.class)
            .hasMessageContaining("5");
    }

    @Test
    void successfulVerificationReturnsOpaqueToken() {
        when(orderRepository.findByOrderNumber("ORD-GUEST-1"))
            .thenReturn(Optional.of(guestOrder()));
        when(verificationService.verifyLatest(7L, "guest@example.com", "123456"))
            .thenReturn(new GuestOrderOtpVerificationService.VerificationResult(
                GuestOrderOtpVerificationService.VerificationStatus.VERIFIED,
                "A".repeat(43)));

        assertThat(service.verifyOtp(
            "ORD-GUEST-1", "guest@example.com", "123456", "192.0.2.10"))
            .isEqualTo("A".repeat(43));
    }

    private static Order guestOrder() {
        return Order.builder()
            .id(7L)
            .orderNumber("ORD-GUEST-1")
            .customerName("Khách Beauty")
            .customerEmail("guest@example.com")
            .build();
    }
}
