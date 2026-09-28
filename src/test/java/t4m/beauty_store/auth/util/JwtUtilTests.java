package t4m.beauty_store.auth.util;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTests {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createsAndReadsSignedToken() {
        JwtUtil jwt = new JwtUtil(SECRET, Duration.ofMinutes(5));

        String token = jwt.generateToken("customer@example.test", Set.of("ROLE_USER"));

        assertThat(jwt.extractUsername(token)).isEqualTo("customer@example.test");
        assertThat(jwt.isTokenValid(token, "customer@example.test")).isTrue();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtUtil issuer = new JwtUtil(SECRET, Duration.ofMinutes(5));
        JwtUtil verifier = new JwtUtil("abcdef0123456789abcdef0123456789", Duration.ofMinutes(5));

        String token = issuer.generateToken("customer@example.test", Set.of("ROLE_USER"));

        assertThatThrownBy(() -> verifier.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtUtil jwt = new JwtUtil(SECRET, Duration.ofMillis(-1));
        String token = jwt.generateToken("customer@example.test", Set.of("ROLE_USER"));

        assertThatThrownBy(() -> jwt.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void refusesWeakSecret() {
        JwtUtil jwt = new JwtUtil("too-short", Duration.ofMinutes(5));

        assertThatThrownBy(() -> jwt.generateToken("customer@example.test", Set.of("ROLE_USER")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsTokenAfterAuthenticationVersionChanges() {
        JwtUtil jwt = new JwtUtil(SECRET, Duration.ofMinutes(5));
        String token = jwt.generateToken("customer@example.test", Set.of("ROLE_USER"), 3);

        assertThat(jwt.isTokenValid(token, "customer@example.test", 3)).isTrue();
        assertThat(jwt.isTokenValid(token, "customer@example.test", 4)).isFalse();
    }
}
