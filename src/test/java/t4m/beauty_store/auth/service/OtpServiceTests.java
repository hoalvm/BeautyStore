package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
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
}
