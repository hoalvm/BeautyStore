package t4m.beauty_store.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import t4m.beauty_store.auth.exception.OtpExpiredException;
import t4m.beauty_store.auth.exception.OtpInvalidException;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_EMAIL_REQUESTS_PER_WINDOW = 5;
    private static final int MAX_CLIENT_REQUESTS_PER_WINDOW = 20;

    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Cache<String, OtpData> otpCache;
    private final Cache<String, Boolean> resendCooldown;
    private final Cache<String, AtomicInteger> emailRequestCounts;
    private final Cache<String, AtomicInteger> clientRequestCounts;

    public OtpService(EmailService emailService, PasswordEncoder passwordEncoder) {
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.otpCache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000).build();
        this.resendCooldown = Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(2000).build();
        this.emailRequestCounts = Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(5000).build();
        this.clientRequestCounts = Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(5000).build();
    }

    public void issueOtp(String email, String purpose) {
        String otp = generateOtp();
        String challengeId = storeOtp(email, otp, purpose);
        String action = "activation".equalsIgnoreCase(purpose)
                ? "đăng ký tài khoản" : "đặt lại mật khẩu";
        try {
            CompletableFuture<Void> delivery = emailService.sendOtpEmail(email, otp, action);
            if (delivery == null) {
                invalidateChallenge(email, purpose, challengeId);
                return;
            }
            delivery.whenComplete((ignored, error) -> {
                if (error != null) invalidateChallenge(email, purpose, challengeId);
            });
        } catch (RuntimeException exception) {
            invalidateChallenge(email, purpose, challengeId);
            throw exception;
        }
    }

    String generateOtp() {
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    String storeOtp(String email, String otp, String purpose) {
        String key = cacheKey(email, purpose);
        if (resendCooldown.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            throw new OtpRateLimitException("Vui lòng chờ 60 giây trước khi yêu cầu mã OTP mới");
        }
        int requestCount = emailRequestCounts.asMap()
                .computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (requestCount > MAX_EMAIL_REQUESTS_PER_WINDOW) {
            throw new OtpRateLimitException("Bạn đã yêu cầu quá nhiều mã OTP. Vui lòng thử lại sau");
        }
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("OTP must contain exactly 6 digits");
        }
        String challengeId = UUID.randomUUID().toString();
        otpCache.put(key, new OtpData(challengeId, passwordEncoder.encode(otp)));
        log.debug("Stored a hashed OTP challenge for purpose {}", normalizePurpose(purpose));
        return challengeId;
    }

    public void validateOtp(String email, String otp, String purpose) {
        String key = cacheKey(email, purpose);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        // Verify and consume while holding the cache's per-key lock. A second
        // concurrent request must not validate the same one-time challenge.
        otpCache.asMap().compute(key, (ignored, data) -> {
            if (data == null) {
                failure.set(new OtpExpiredException("Mã OTP không tồn tại hoặc đã hết hạn"));
                return null;
            }
            if (otp == null || !otp.matches("\\d{6}") || !passwordEncoder.matches(otp, data.otpHash)) {
                if (data.failedAttempts.incrementAndGet() >= MAX_VERIFICATION_ATTEMPTS) {
                    failure.set(new OtpRateLimitException(
                        "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu mã mới"));
                    return null;
                }
                failure.set(new OtpInvalidException("Mã OTP không chính xác"));
                return data;
            }
            return null;
        });
        if (failure.get() != null) throw failure.get();
        log.debug("OTP challenge validated for purpose {}", normalizePurpose(purpose));
    }

    public void checkClientRequestLimit(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) return;
        int count = clientRequestCounts.asMap()
                .computeIfAbsent(clientAddress.trim(), ignored -> new AtomicInteger()).incrementAndGet();
        if (count > MAX_CLIENT_REQUESTS_PER_WINDOW) {
            throw new OtpRateLimitException("Có quá nhiều yêu cầu OTP. Vui lòng thử lại sau");
        }
    }

    private void invalidateChallenge(String email, String purpose, String challengeId) {
        String key = cacheKey(email, purpose);
        otpCache.asMap().computeIfPresent(key, (ignored, current) -> {
            if (!current.challengeId.equals(challengeId)) return current;
            log.warn("Invalidated an OTP challenge after email delivery failed");
            return null;
        });
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
        private final String challengeId;
        private final String otpHash;
        private final AtomicInteger failedAttempts = new AtomicInteger();

        private OtpData(String challengeId, String otpHash) {
            this.challengeId = challengeId;
            this.otpHash = otpHash;
        }
    }
}
