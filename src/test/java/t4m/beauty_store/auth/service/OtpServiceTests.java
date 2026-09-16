package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.exception.OtpExpiredException;
import t4m.beauty_store.auth.exception.OtpInvalidException;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OtpServiceTests {

    private final OtpService service = new OtpService(mock(EmailService.class));

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
}
