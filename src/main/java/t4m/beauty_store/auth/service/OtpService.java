package t4m.beauty_store.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import t4m.beauty_store.auth.exception.OtpInvalidException;
import t4m.beauty_store.auth.exception.OtpExpiredException;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {
    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_EMAIL_REQUESTS_PER_WINDOW = 5;
    private static final int MAX_CLIENT_REQUESTS_PER_WINDOW = 20;

    private final EmailService emailService;
    private final Cache<String, OtpData> otpCache;
    private final Cache<String, Boolean> resendCooldown;
    private final Cache<String, AtomicInteger> emailRequestCounts;
    private final Cache<String, AtomicInteger> clientRequestCounts;

    @Autowired
    public OtpService(EmailService emailService) {
        this.emailService = emailService;
        this.otpCache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000).build();
        this.resendCooldown = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(2000)
                .build();
        this.emailRequestCounts = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
        this.clientRequestCounts = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
    }

    public String generateOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }

    @Async
    public void sendOtpEmail(String email, String otp, String purpose) {
        String action = "Account Activation".equalsIgnoreCase(purpose)
                ? "đăng ký tài khoản"
                : "đặt lại mật khẩu";
        emailService.sendOtpEmail(email, otp, action);
    }

    public void storeOtp(String email, String otp, String purpose) {
        String key = cacheKey(email, purpose);
        if (resendCooldown.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            throw new OtpRateLimitException("Vui lòng chờ 60 giây trước khi yêu cầu mã OTP mới");
        }

        int requestCount = emailRequestCounts.asMap()
                .computeIfAbsent(key, ignored -> new AtomicInteger())
                .incrementAndGet();
        if (requestCount > MAX_EMAIL_REQUESTS_PER_WINDOW) {
            throw new OtpRateLimitException("Bạn đã yêu cầu quá nhiều mã OTP. Vui lòng thử lại sau");
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("OTP must contain exactly 6 digits");
        }
        otpCache.put(key, new OtpData(otp));
        logger.debug("Stored a new OTP challenge for purpose {}", normalizePurpose(purpose));
    }

    public void validateOtp(String email, String otp, String purpose) {
        String key = cacheKey(email, purpose);
        OtpData otpData = otpCache.getIfPresent(key);
        if (otpData == null) {
            throw new OtpExpiredException("Mã OTP không tồn tại hoặc đã hết hạn");
        }

        byte[] expected = otpData.otp.getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = otp == null ? new byte[0] : otp.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, supplied)) {
            if (otpData.failedAttempts.incrementAndGet() >= MAX_VERIFICATION_ATTEMPTS) {
                otpCache.invalidate(key);
                throw new OtpRateLimitException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu mã mới");
            }
            throw new OtpInvalidException("Mã OTP không chính xác");
        }
        otpCache.invalidate(key);
        logger.debug("OTP challenge validated for purpose {}", normalizePurpose(purpose));
    }

    /**
     * Adds an IP/client-window limit without trusting forwarding headers. Call this at
     * anonymous OTP HTTP entry points before looking up an account.
     */
    public void checkClientRequestLimit(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return;
        }
        String key = clientAddress.trim();
        int requestCount = clientRequestCounts.asMap()
                .computeIfAbsent(key, ignored -> new AtomicInteger())
                .incrementAndGet();
        if (requestCount > MAX_CLIENT_REQUESTS_PER_WINDOW) {
            throw new OtpRateLimitException("Có quá nhiều yêu cầu OTP. Vui lòng thử lại sau");
        }
    }

    private String cacheKey(String email, String purpose) {
        if (email == null || email.isBlank() || purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Email and OTP purpose are required");
        }
        return email.trim().toLowerCase(Locale.ROOT) + ':' + normalizePurpose(purpose);
    }

    private String normalizePurpose(String purpose) {
        return purpose == null ? "" : purpose.trim().toLowerCase(Locale.ROOT);
    }

    private static final class OtpData {
        private final String otp;
        private final AtomicInteger failedAttempts = new AtomicInteger();

        private OtpData(String otp) {
            this.otp = otp;
        }
    }
}
