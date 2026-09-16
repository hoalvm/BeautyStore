package t4m.beauty_store.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.exception.OtpRateLimitException;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;
import t4m.beauty_store.order.repository.OrderRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GuestOrderAccessService {
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final OrderRepository orderRepository;
    private final GuestOrderAccessRepository accessRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final GuestOrderOtpVerificationService otpVerificationService;
    private final GuestOrderOtpRateLimiter otpRateLimiter;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestOtp(String orderNumber, String email) {
        requestOtp(orderNumber, email, null);
    }

    /**
     * Always returns normally for an unknown order/email pair so the public
     * endpoint cannot be used to enumerate guest orders.
     */
    @Transactional
    public void requestOtp(String orderNumber, String email, String clientAddress) {
        String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
        String normalizedEmail = normalizeEmail(email);
        otpRateLimiter.checkOtpRequest(normalizedEmail, clientAddress);

        Order order = orderRepository.findByOrderNumberForUpdate(normalizedOrderNumber)
            .filter(candidate -> candidate.getUser() == null)
            .filter(candidate -> candidate.getCustomerEmail().equalsIgnoreCase(normalizedEmail))
            .orElse(null);
        if (order == null) return;

        LocalDateTime now = LocalDateTime.now();
        boolean coolingDown = accessRepository
            .findTopByOrderIdAndEmailIgnoreCaseOrderByCreatedAtDesc(order.getId(), normalizedEmail)
            .map(GuestOrderAccess::getResendAvailableAt)
            .filter(availableAt -> availableAt.isAfter(now))
            .isPresent();
        if (coolingDown) return;

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        GuestOrderAccess access = GuestOrderAccess.builder()
            .order(order)
            .email(normalizedEmail)
            .otpHash(passwordEncoder.encode(otp))
            .expiresAt(now.plus(OTP_LIFETIME))
            .resendAvailableAt(now.plus(RESEND_DELAY))
            .build();
        accessRepository.save(access);
        emailService.sendGuestOrderOtp(
            normalizedEmail, order.getCustomerName(), order.getOrderNumber(), otp);
    }

    public String verifyOtp(String orderNumber, String email, String otp) {
        return verifyOtp(orderNumber, email, otp, null);
    }

    public String verifyOtp(String orderNumber, String email, String otp, String clientAddress) {
        String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
        String normalizedEmail = normalizeEmail(email);
        otpRateLimiter.checkOtpVerification(normalizedEmail, clientAddress);
        Order order = findGuestOrder(normalizedOrderNumber, normalizedEmail);
        GuestOrderOtpVerificationService.VerificationResult result =
            otpVerificationService.verifyLatest(order.getId(), normalizedEmail, otp);
        return switch (result.status()) {
            case VERIFIED -> result.accessToken();
            case LOCKED -> throw new OtpRateLimitException(
                "Bạn đã nhập sai OTP quá 5 lần. Vui lòng yêu cầu mã mới");
            case INVALID -> throw invalidOtp();
        };
    }

    @Transactional(readOnly = true)
    public Order requireOrderAccess(String orderNumber, String rawToken) {
        return requireAccess(normalizeOrderNumber(orderNumber), rawToken).getOrder();
    }

    @Transactional(readOnly = true)
    public OrderResponse requireOrderAccessResponse(String orderNumber, String rawToken) {
        return OrderResponse.fromEntity(
            requireAccess(normalizeOrderNumber(orderNumber), rawToken).getOrder());
    }

    private GuestOrderAccess requireAccess(String orderNumber, String rawToken) {
        if (rawToken == null || !TOKEN_PATTERN.matcher(rawToken).matches()) {
            throw new IllegalArgumentException("Mã truy cập đơn hàng không hợp lệ hoặc đã hết hạn");
        }
        LocalDateTime now = LocalDateTime.now();
        return accessRepository
            .findTopByOrderOrderNumberAndAccessTokenHashOrderByCreatedAtDesc(
                orderNumber, sha256(rawToken))
            .filter(candidate -> candidate.getVerifiedAt() != null)
            .filter(candidate -> candidate.getAccessTokenExpiresAt() != null
                && candidate.getAccessTokenExpiresAt().isAfter(now))
            .filter(candidate -> candidate.getOrder().getUser() == null)
            .orElseThrow(() -> new IllegalArgumentException(
                "Mã truy cập đơn hàng không hợp lệ hoặc đã hết hạn"));
    }

    private Order findGuestOrder(String orderNumber, String email) {
        return orderRepository.findByOrderNumber(orderNumber)
            .filter(order -> order.getUser() == null)
            .filter(order -> order.getCustomerEmail().equalsIgnoreCase(email))
            .orElseThrow(GuestOrderAccessService::invalidOtp);
    }

    private static IllegalArgumentException invalidOtp() {
        return new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOrderNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
