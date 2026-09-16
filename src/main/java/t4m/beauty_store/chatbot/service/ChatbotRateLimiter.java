package t4m.beauty_store.chatbot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import t4m.beauty_store.config.PublicApiRateLimitException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** A bounded, fixed-window limiter for the anonymous, cost-bearing AI endpoint. */
@Component
public class ChatbotRateLimiter {
    private static final int MAX_TRACKED_CLIENTS = 20_000;

    private final int maximumRequests;
    private final Cache<String, AtomicInteger> requestCounts;

    public ChatbotRateLimiter(
            @Value("${chatbot.rate-limit.max-requests:20}") int maximumRequests,
            @Value("${chatbot.rate-limit.window:PT1M}") Duration window) {
        if (maximumRequests < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Cấu hình rate limit chatbot không hợp lệ");
        }
        this.maximumRequests = maximumRequests;
        this.requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(window)
            .maximumSize(MAX_TRACKED_CLIENTS)
            .build();
    }

    public void check(String remoteAddress) {
        String identity = remoteAddress == null || remoteAddress.isBlank()
            ? "unknown-client"
            : remoteAddress.trim();
        int count = requestCounts.asMap()
            .computeIfAbsent(sha256(identity), ignored -> new AtomicInteger())
            .incrementAndGet();
        if (count > maximumRequests) {
            throw new PublicApiRateLimitException(
                "Bạn đã gửi quá nhiều tin nhắn. Vui lòng thử lại sau");
        }
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
