package t4m.beauty_store.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;
import t4m.beauty_store.config.StoreTime;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestOrderOtpVerificationServiceTests {
    private GuestOrderAccessRepository accessRepository;
    private PasswordEncoder passwordEncoder;
    private GuestOrderOtpVerificationService service;
    private StoreTime storeTime;

    @BeforeEach
    void setUp() {
        accessRepository = mock(GuestOrderAccessRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        storeTime = fixedTime();
        service = new GuestOrderOtpVerificationService(accessRepository, passwordEncoder, storeTime);
    }

    @Test
    void invalidAttemptIsFlushedBeforeReturningAnErrorResult() {
        GuestOrderAccess access = activeChallenge(0);
        when(accessRepository.findLatestForUpdate(7L, "guest@example.com"))
            .thenReturn(Optional.of(access));
        when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

        GuestOrderOtpVerificationService.VerificationResult result =
            service.verifyLatest(7L, "guest@example.com", "000000");

        assertThat(result.status())
            .isEqualTo(GuestOrderOtpVerificationService.VerificationStatus.INVALID);
        assertThat(access.getAttempts()).isEqualTo(1);
        verify(accessRepository).saveAndFlush(access);
    }

    @Test
    void fifthInvalidAttemptIsPersistedAndLocksChallenge() {
        GuestOrderAccess access = activeChallenge(4);
        when(accessRepository.findLatestForUpdate(7L, "guest@example.com"))
            .thenReturn(Optional.of(access));
        when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

        GuestOrderOtpVerificationService.VerificationResult result =
            service.verifyLatest(7L, "guest@example.com", "000000");

        assertThat(result.status())
            .isEqualTo(GuestOrderOtpVerificationService.VerificationStatus.LOCKED);
        assertThat(access.getAttempts()).isEqualTo(5);
        verify(accessRepository).saveAndFlush(access);
    }

    @Test
    void lockedChallengeCannotBeRecoveredWithCorrectOtp() {
        GuestOrderAccess access = activeChallenge(5);
        when(accessRepository.findLatestForUpdate(7L, "guest@example.com"))
            .thenReturn(Optional.of(access));

        GuestOrderOtpVerificationService.VerificationResult result =
            service.verifyLatest(7L, "guest@example.com", "123456");

        assertThat(result.status())
            .isEqualTo(GuestOrderOtpVerificationService.VerificationStatus.LOCKED);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(accessRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiredChallengeRejectsEvenTheCorrectOtp() {
        GuestOrderAccess access = activeChallenge(0);
        access.setExpiresAt(storeTime.currentDateTime().minusSeconds(1));
        when(accessRepository.findLatestForUpdate(7L, "guest@example.com"))
            .thenReturn(Optional.of(access));

        GuestOrderOtpVerificationService.VerificationResult result =
            service.verifyLatest(7L, "guest@example.com", "123456");

        assertThat(result.status())
            .isEqualTo(GuestOrderOtpVerificationService.VerificationStatus.INVALID);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(accessRepository, never()).saveAndFlush(any());
    }

    @Test
    void successfulOtpCreatesThirtyMinuteSingleUseToken() {
        GuestOrderAccess access = activeChallenge(2);
        when(accessRepository.findLatestForUpdate(7L, "guest@example.com"))
            .thenReturn(Optional.of(access));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);
        GuestOrderOtpVerificationService.VerificationResult result =
            service.verifyLatest(7L, "guest@example.com", "123456");
        assertThat(result.status())
            .isEqualTo(GuestOrderOtpVerificationService.VerificationStatus.VERIFIED);
        assertThat(result.accessToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(access.getAccessTokenHash()).matches("[a-f0-9]{64}");
        assertThat(access.getVerifiedAt()).isEqualTo(storeTime.currentDateTime());
        assertThat(access.getExpiresAt()).isEqualTo(access.getVerifiedAt());
        assertThat(access.getAccessTokenExpiresAt())
            .isEqualTo(access.getVerifiedAt().plusMinutes(30));
        verify(accessRepository).saveAndFlush(access);
    }

    @Test
    void verificationWriterAlwaysUsesRequiresNewTransaction() throws Exception {
        Method method = GuestOrderOtpVerificationService.class.getMethod(
            "verifyLatest", Long.class, String.class, String.class);

        Transactional annotation = method.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private GuestOrderAccess activeChallenge(int attempts) {
        return GuestOrderAccess.builder()
            .id(11L)
            .email("guest@example.com")
            .otpHash("otp-hash")
            .expiresAt(storeTime.currentDateTime().plusMinutes(5))
            .resendAvailableAt(storeTime.currentDateTime().plusSeconds(60))
            .attempts(attempts)
            .build();
    }

    private static StoreTime fixedTime() {
        return new StoreTime(Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), StoreTime.ZONE));
    }
}
