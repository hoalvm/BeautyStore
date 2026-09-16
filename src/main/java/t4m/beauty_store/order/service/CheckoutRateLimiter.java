package t4m.beauty_store.order.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.config.PublicApiRateLimitException;
import t4m.beauty_store.order.dto.CheckoutRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded checkout throttling without retaining raw customer identifiers. */
@Component
public class CheckoutRateLimiter {
    private static final int MAX_TRACKED_IDENTITIES = 50_000;

    private final int maximumPerIp;
    private final int maximumPerContact;
    private final Cache<String, AtomicInteger> ipCounts;
    private final Cache<String, AtomicInteger> contactCounts;

    public CheckoutRateLimiter(
            @Value("${checkout.rate-limit.max-per-ip:10}") int maximumPerIp,
            @Value("${checkout.rate-limit.max-per-contact:5}") int maximumPerContact,
            @Value("${checkout.rate-limit.window:PT15M}") Duration window) {
        if (maximumPerIp < 1 || maximumPerContact < 1 || window == null
                || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Cấu hình rate limit checkout không hợp lệ");
        }
        this.maximumPerIp = maximumPerIp;
        this.maximumPerContact = maximumPerContact;
        this.ipCounts = newCache(window);
        this.contactCounts = newCache(window);
    }

    public void check(CartIdentity identity, CheckoutRequest request, String remoteAddress) {
        int ipCount = increment(ipCounts, normalize(remoteAddress, "unknown-client"));
        String email = request == null ? "" : normalize(request.getCustomerEmail(), "missing-email");
        String phone = request == null ? "" : normalize(request.getCustomerPhone(), "missing-phone");
        String cartIdentity = identity != null && identity.authenticated()
            ? "member:" + normalize(identity.userEmail(), "missing-member")
            : "guest:" + normalize(identity == null ? null : identity.guestTokenHash(), "missing-guest");
        int emailCount = increment(contactCounts, "email:" + email);
        int phoneCount = increment(contactCounts, "phone:" + phone);
        int identityCount = increment(contactCounts, cartIdentity);
        if (ipCount > maximumPerIp || emailCount > maximumPerContact
                || phoneCount > maximumPerContact || identityCount > maximumPerContact) {
            throw new PublicApiRateLimitException(
                "Bạn đã tạo quá nhiều lượt checkout. Vui lòng thử lại sau");
        }
    }

    private static Cache<String, AtomicInteger> newCache(Duration window) {
        return Caffeine.newBuilder()
            .expireAfterWrite(window)
            .maximumSize(MAX_TRACKED_IDENTITIES)
            .build();
    }

    private static int increment(Cache<String, AtomicInteger> cache, String identity) {
        return cache.asMap().computeIfAbsent(sha256(identity), ignored -> new AtomicInteger())
            .incrementAndGet();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
            ? fallback : value.trim().toLowerCase(Locale.ROOT);
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
