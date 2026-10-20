package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class CompatiblePasswordEncoderTests {
    private final CompatiblePasswordEncoder passwords = new CompatiblePasswordEncoder(4);

    @Test
    void supportsFull128CharacterPolicyWithoutTruncation() {
        String first = "Aa1!" + "a".repeat(123) + "x";
        String second = "Aa1!" + "a".repeat(123) + "y";

        String encoded = passwords.encode(first);

        assertThat(encoded).startsWith("{bcrypt-sha256}$2");
        assertThat(passwords.matches(first, encoded)).isTrue();
        assertThat(passwords.matches(second, encoded)).isFalse();
        assertThat(passwords.upgradeEncoding(encoded)).isFalse();
    }

    @Test
    void supportsMultibytePasswordsLongerThan72Utf8Bytes() {
        String password = "ĐẹpDa1!" + "á".repeat(60);
        String encoded = passwords.encode(password);

        assertThat(passwords.matches(password, encoded)).isTrue();
        assertThat(passwords.matches(password + "a", encoded)).isFalse();
    }

    @Test
    void existingUnprefixedBcryptPasswordsStillLogin() {
        String legacy = new BCryptPasswordEncoder(4).encode("OldSecret@123");

        assertThat(passwords.matches("OldSecret@123", legacy)).isTrue();
        assertThat(passwords.matches("WrongSecret@123", legacy)).isFalse();
        assertThat(passwords.matches("A".repeat(100), legacy)).isFalse();
        assertThat(passwords.upgradeEncoding(legacy)).isTrue();
    }

    @Test
    void eachNewHashIsSaltedAndUnknownFormatsFailClosed() {
        String first = passwords.encode("Secret@123");
        String second = passwords.encode("Secret@123");

        assertThat(first).isNotEqualTo(second);
        assertThat(passwords.matches("Secret@123", second)).isTrue();
        assertThat(passwords.matches("Secret@123", "{unknown}hash")).isFalse();
        assertThat(passwords.matches(null, first)).isFalse();
    }
}
