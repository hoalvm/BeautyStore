package t4m.beauty_store.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Persists each OTP verification outcome in its own transaction. Invalid OTPs
 * are returned as a result instead of being thrown here so their attempt count
 * commits before the API layer reports an error.
 */
@Service
@RequiredArgsConstructor
public class GuestOrderOtpVerificationService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GuestOrderAccessRepository accessRepository;
    private final PasswordEncoder passwordEncoder;
    private final StoreTime storeTime;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VerificationResult verifyLatest(Long orderId, String email, String suppliedOtp) {
        GuestOrderAccess access = accessRepository.findLatestForUpdate(orderId, email)
            .orElse(null);
        if (access == null) {
            return VerificationResult.invalid();
        }

        var now = storeTime.currentDateTime();
        if (access.getVerifiedAt() != null || !access.getExpiresAt().isAfter(now)) {
            return VerificationResult.invalid();
        }

        int attempts = access.getAttempts() == null ? 0 : access.getAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            return VerificationResult.locked();
        }

        String candidate = suppliedOtp == null ? "" : suppliedOtp;
        if (!passwordEncoder.matches(candidate, access.getOtpHash())) {
            int updatedAttempts = attempts + 1;
            access.setAttempts(updatedAttempts);
            accessRepository.saveAndFlush(access);
            return updatedAttempts >= MAX_ATTEMPTS
                ? VerificationResult.locked()
                : VerificationResult.invalid();
        }

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String accessToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        access.setVerifiedAt(now);
        access.setExpiresAt(now);
        access.setAccessTokenHash(sha256(accessToken));
        access.setAccessTokenExpiresAt(now.plus(ACCESS_LIFETIME));
        accessRepository.saveAndFlush(access);
        return VerificationResult.verified(accessToken);
    }

    public enum VerificationStatus {
        VERIFIED,
        INVALID,
        LOCKED
    }

    public record VerificationResult(VerificationStatus status, String accessToken) {
        static VerificationResult verified(String token) {
            return new VerificationResult(VerificationStatus.VERIFIED, token);
        }

        static VerificationResult invalid() {
            return new VerificationResult(VerificationStatus.INVALID, null);
        }

        static VerificationResult locked() {
            return new VerificationResult(VerificationStatus.LOCKED, null);
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
