package t4m.beauty_store.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Issues a short-lived, HttpOnly browser credential for server-rendered pages.
 * Mutation APIs still require the bearer header, so the cookie cannot be used
 * to perform cross-site state changes.
 */
@Service
public class AuthCookieService {
    public static final String COOKIE_NAME = "BEAUTYSTORE_AUTH";
    private static final Duration MAX_AGE = Duration.ofHours(6);

    private final boolean secure;

    public AuthCookieService(@Value("${security.jwt.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String jwt) {
        return cookie(jwt, MAX_AGE);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
    }
}
