package t4m.beauty_store.auth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTests {
    @Test
    void acceptsStrongPassword() {
        assertThat(PasswordPolicy.isStrong("Beauty@2026")).isTrue();
    }

    @Test
    void rejectsMissingCharacterClasses() {
        assertThat(PasswordPolicy.isStrong("beauty@2026")).isFalse();
        assertThat(PasswordPolicy.isStrong("BEAUTY@2026")).isFalse();
        assertThat(PasswordPolicy.isStrong("BeautyStore@")).isFalse();
        assertThat(PasswordPolicy.isStrong("Beauty2026")).isFalse();
    }

    @Test
    void rejectsTooShortTooLongAndWhitespace() {
        assertThat(PasswordPolicy.isStrong("Be@12")).isFalse();
        assertThat(PasswordPolicy.isStrong("Beauty @2026")).isFalse();
        assertThat(PasswordPolicy.isStrong("B1@" + "a".repeat(126))).isFalse();
    }

    @Test
    void serviceGuardThrowsConsistentBusinessError() {
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("weak"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8-128");
    }
}
