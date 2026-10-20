package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import t4m.beauty_store.auth.exception.OtpExpiredException;
import t4m.beauty_store.auth.exception.OtpInvalidException;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTests {

    private final OtpService service = new OtpService(
        mock(EmailService.class), new BCryptPasswordEncoder(4));

    @Test
    void generatesSixDigitCryptographicChallengeShape() {
        for (int index = 0; index < 100; index++) {
            assertThat(service.generateOtp()).matches("\\d{6}");
        }
    }

    @Test
    void otpCanOnlyBeUsedOnce() {
        service.storeOtp("customer@example.com", "123456", "activation");

        service.validateOtp("CUSTOMER@example.com", "123456", "activation");

        assertThatThrownBy(() ->
                service.validateOtp("customer@example.com", "123456", "activation"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void locksChallengeAfterFiveWrongAttempts() {
        service.storeOtp("customer@example.com", "123456", "reset");

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() ->
                    service.validateOtp("customer@example.com", "000000", "reset"))
                    .isInstanceOf(OtpInvalidException.class);
        }
        assertThatThrownBy(() ->
                service.validateOtp("customer@example.com", "000000", "reset"))
                .isInstanceOf(OtpRateLimitException.class);
        assertThatThrownBy(() ->
                service.validateOtp("customer@example.com", "123456", "reset"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void enforcesSixtySecondResendCooldown() {
        service.storeOtp("customer@example.com", "123456", "activation");

        assertThatThrownBy(() ->
                service.storeOtp("customer@example.com", "654321", "activation"))
                .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    void invalidatesChallengeWhenEmailDeliveryFails() {
        EmailService email = mock(EmailService.class);
        CompletableFuture<Void> failure = new CompletableFuture<>();
        when(email.sendOtpEmail(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(failure);
        OtpService otp = new OtpService(email, new BCryptPasswordEncoder(4));

        otp.issueOtp("customer@example.com", "activation");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendOtpEmail(org.mockito.ArgumentMatchers.eq("customer@example.com"),
                code.capture(), org.mockito.ArgumentMatchers.anyString());
        failure.completeExceptionally(new IllegalStateException("mail unavailable"));

        assertThatThrownBy(() -> otp.validateOtp(
                "customer@example.com", code.getValue(), "activation"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void concurrentVerificationConsumesChallengeExactlyOnce() throws Exception {
        service.storeOtp("customer@example.com", "123456", "activation");
        int requests = 8;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requests)) {
            List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    try {
                        service.validateOtp("customer@example.com", "123456", "activation");
                        return true;
                    } catch (OtpExpiredException expected) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            start.countDown();
        }
    }

    @Test
    void synchronousDeliveryRejectionDoesNotLeaveUsableChallenge() {
        EmailService email = mock(EmailService.class);
        when(email.sendOtpEmail(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("executor unavailable"));
        OtpService otp = new OtpService(email, new BCryptPasswordEncoder(4));

        assertThatThrownBy(() -> otp.issueOtp("customer@example.com", "activation"))
            .isInstanceOf(IllegalStateException.class);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendOtpEmail(org.mockito.ArgumentMatchers.eq("customer@example.com"),
                code.capture(), org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> otp.validateOtp("customer@example.com", code.getValue(), "activation"))
            .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void malformedChallengeCountsAsFailedAttemptWithoutPasswordEncoderFailure() {
        service.storeOtp("customer@example.com", "123456", "activation");

        assertThatThrownBy(() -> service.validateOtp(
            "customer@example.com", "1".repeat(1000), "activation"))
            .isInstanceOf(OtpInvalidException.class);
        service.validateOtp("customer@example.com", "123456", "activation");
    }
}
