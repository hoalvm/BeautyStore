package t4m.beauty_store.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Versioned UTF-8 password hashing without BCrypt's 72-byte input limit.
 * Existing unprefixed BCrypt hashes remain valid; newly written hashes use
 * SHA-256 followed by Base64 so BCrypt always receives 44 printable bytes.
 */
public final class CompatiblePasswordEncoder implements PasswordEncoder {
    private static final String PREFIX = "{bcrypt-sha256}";
    private final BCryptPasswordEncoder bcrypt;

    public CompatiblePasswordEncoder(int strength) {
        bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) throw new IllegalArgumentException("Password is required");
        return PREFIX + bcrypt.encode(prehash(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) return false;
        if (encodedPassword.startsWith(PREFIX)) {
            return bcrypt.matches(prehash(rawPassword), encodedPassword.substring(PREFIX.length()));
        }
        // Unsupported formats fail closed. Long candidates cannot be encoded
        // by the legacy BCrypt implementation and must not cause a login 500.
        if (!encodedPassword.startsWith("$2") ||
                rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) return false;
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.startsWith(PREFIX)) return true;
        return bcrypt.upgradeEncoding(encodedPassword.substring(PREFIX.length()));
    }

    private static String prehash(CharSequence value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
