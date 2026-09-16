package t4m.beauty_store.voucher.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Builds the non-reversible customer key used by voucher usage limits. */
public final class VoucherCustomerIdentity {
    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private VoucherCustomerIdentity() {
    }

    public static String emailHash(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String normalizeHash(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SHA_256_HEX.matcher(normalized).matches() ? normalized : null;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return Normalizer.normalize(email.trim(), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
    }
}
