package t4m.beauty_store.order.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestOrderOtpRateLimiterTests {

    @Test
    void requestLimitIsIndependentPerNormalizedEmail() {
        GuestOrderOtpRateLimiter limiter = new GuestOrderOtpRateLimiter();

        for (int request = 0; request < GuestOrderOtpRateLimiter.MAX_REQUESTS_PER_EMAIL; request++) {
            limiter.checkOtpRequest(
                request % 2 == 0 ? "CUSTOMER@example.com" : "customer@example.com",
                "198.51.100." + request);
        }

        assertThatThrownBy(() ->
            limiter.checkOtpRequest(" customer@example.com ", "203.0.113.50"))
            .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    void requestLimitIsIndependentPerIpAcrossDifferentEmails() {
        GuestOrderOtpRateLimiter limiter = new GuestOrderOtpRateLimiter();

        for (int request = 0; request < GuestOrderOtpRateLimiter.MAX_REQUESTS_PER_IP; request++) {
            limiter.checkOtpRequest("customer-" + request + "@example.com", "203.0.113.9");
        }

        assertThatThrownBy(() ->
            limiter.checkOtpRequest("one-more@example.com", " 203.0.113.9 "))
            .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    void verificationHasItsOwnPerEmailLimit() {
        GuestOrderOtpRateLimiter limiter = new GuestOrderOtpRateLimiter();

        for (int request = 0; request < GuestOrderOtpRateLimiter.MAX_VERIFICATIONS_PER_EMAIL; request++) {
            limiter.checkOtpVerification("customer@example.com", "198.51.100." + request);
        }

        assertThatThrownBy(() ->
            limiter.checkOtpVerification("CUSTOMER@example.com", "203.0.113.50"))
            .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    void verificationHasItsOwnPerIpLimit() {
        GuestOrderOtpRateLimiter limiter = new GuestOrderOtpRateLimiter();

        for (int request = 0; request < GuestOrderOtpRateLimiter.MAX_VERIFICATIONS_PER_IP; request++) {
            limiter.checkOtpVerification("customer-" + request + "@example.com", "192.0.2.44");
        }

        assertThatThrownBy(() ->
            limiter.checkOtpVerification("one-more@example.com", "192.0.2.44"))
            .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    void requestAndVerificationBucketsDoNotConsumeEachOther() {
        GuestOrderOtpRateLimiter limiter = new GuestOrderOtpRateLimiter();

        for (int request = 0; request < GuestOrderOtpRateLimiter.MAX_REQUESTS_PER_EMAIL; request++) {
            limiter.checkOtpRequest("customer@example.com", null);
        }

        assertThatThrownBy(() -> limiter.checkOtpRequest("customer@example.com", null))
            .isInstanceOf(OtpRateLimitException.class);
        assertThatCode(() -> limiter.checkOtpVerification("customer@example.com", null))
            .doesNotThrowAnyException();
    }
}
