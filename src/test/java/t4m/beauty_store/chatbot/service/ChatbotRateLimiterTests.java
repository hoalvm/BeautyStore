package t4m.beauty_store.chatbot.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.config.PublicApiRateLimitException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatbotRateLimiterTests {

    @Test
    void rejectsOnlyTheClientThatExceedsItsBoundedWindow() {
        ChatbotRateLimiter limiter = new ChatbotRateLimiter(2, Duration.ofMinutes(1));

        limiter.check("203.0.113.10");
        limiter.check("203.0.113.10");

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
            .isInstanceOf(PublicApiRateLimitException.class);
        assertThatCode(() -> limiter.check("203.0.113.11"))
            .doesNotThrowAnyException();
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> new ChatbotRateLimiter(0, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatbotRateLimiter(1, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
