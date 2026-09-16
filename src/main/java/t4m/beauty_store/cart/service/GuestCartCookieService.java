package t4m.beauty_store.cart.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
public class GuestCartCookieService {
    public static final String COOKIE_NAME = "beauty_cart";
    private static final Duration LIFETIME = Duration.ofDays(30);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean secure;

    public GuestCartCookieService(@Value("${store.cookies.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String resolveTokenHash(HttpServletRequest request, HttpServletResponse response) {
        String token = resolveToken(request);
        if (token == null) {
            token = newToken();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, LIFETIME).toString());
        }
        return sha256(token);
    }

    /** Return the pre-login guest identity without creating a new cookie. */
    public String resolveExistingTokenHash(HttpServletRequest request) {
        String token = resolveToken(request);
        return token == null ? null : sha256(token);
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private static String resolveToken(HttpServletRequest request) {
        return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
            .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && TOKEN_PATTERN.matcher(value).matches())
            .findFirst()
            .orElse(null);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
