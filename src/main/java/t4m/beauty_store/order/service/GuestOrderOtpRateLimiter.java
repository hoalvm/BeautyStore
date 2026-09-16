package t4m.beauty_store.order.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory fixed-window limits for the public guest-order OTP endpoints. */
@Component
public class GuestOrderOtpRateLimiter {
    static final int MAX_REQUESTS_PER_EMAIL = 5;
    static final int MAX_REQUESTS_PER_IP = 20;
    static final int MAX_VERIFICATIONS_PER_EMAIL = 10;
    static final int MAX_VERIFICATIONS_PER_IP = 30;

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_IDENTITIES = 20_000;

    private final Cache<String, AtomicInteger> requestEmailCounts = newCounterCache();
    private final Cache<String, AtomicInteger> requestIpCounts = newCounterCache();
    private final Cache<String, AtomicInteger> verificationEmailCounts = newCounterCache();
    private final Cache<String, AtomicInteger> verificationIpCounts = newCounterCache();

    public void checkOtpRequest(String email, String clientAddress) {
        int emailCount = increment(requestEmailCounts, normalizeEmail(email));
        int ipCount = incrementIfPresent(requestIpCounts, clientAddress);
        rejectWhenExceeded(emailCount, MAX_REQUESTS_PER_EMAIL, ipCount, MAX_REQUESTS_PER_IP);
    }

    public void checkOtpVerification(String email, String clientAddress) {
        int emailCount = increment(verificationEmailCounts, normalizeEmail(email));
        int ipCount = incrementIfPresent(verificationIpCounts, clientAddress);
        rejectWhenExceeded(
            emailCount, MAX_VERIFICATIONS_PER_EMAIL,
            ipCount, MAX_VERIFICATIONS_PER_IP);
    }

    private static Cache<String, AtomicInteger> newCounterCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(WINDOW)
            .maximumSize(MAX_IDENTITIES)
            .build();
    }

    private static int increment(Cache<String, AtomicInteger> cache, String identity) {
        String key = sha256(identity);
        return cache.asMap()
            .computeIfAbsent(key, ignored -> new AtomicInteger())
            .incrementAndGet();
    }

    private static int incrementIfPresent(Cache<String, AtomicInteger> cache, String identity) {
        if (identity == null || identity.isBlank()) {
            return 0;
        }
        return increment(cache, identity.trim());
    }

    private static void rejectWhenExceeded(
            int firstCount, int firstMaximum, int secondCount, int secondMaximum) {
        if (firstCount > firstMaximum || secondCount > secondMaximum) {
            throw new OtpRateLimitException(
                "Bạn đã thực hiện quá nhiều yêu cầu OTP. Vui lòng thử lại sau");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
