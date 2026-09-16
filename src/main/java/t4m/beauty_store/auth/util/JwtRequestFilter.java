package t4m.beauty_store.auth.util;

import io.jsonwebtoken.JwtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import t4m.beauty_store.auth.service.AuthCookieService;
import t4m.beauty_store.config.ApiErrorResponse;
import t4m.beauty_store.config.RequestTraceFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtRequestFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        boolean browserCookie = false;
        String jwt;

        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
                reject(request, response, "Unsupported authorization scheme");
                return;
            }
            jwt = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            if (jwt.isEmpty()) {
                reject(request, response, "Missing bearer token");
                return;
            }
        } else {
            // The HttpOnly cookie is intentionally accepted for safe navigation only.
            // Mutating APIs still require an explicit bearer token, preventing ambient
            // authentication cookies from being used for cross-site state changes.
            jwt = SAFE_METHODS.contains(request.getMethod()) ? cookieToken(request) : null;
            browserCookie = jwt != null;
            if (jwt == null) {
                chain.doFilter(request, response);
                return;
            }
        }

        try {
            String username = jwtUtil.extractUsername(jwt);
            if (username == null || username.isBlank()) {
                reject(request, response, "Invalid bearer token");
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtUtil.isTokenValid(jwt, username) && isAccountUsable(userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    SecurityContextHolder.clearContext();
                    if (!browserCookie) {
                        reject(request, response, "Invalid bearer token or unavailable account");
                        return;
                    }
                }
            }
        } catch (JwtException e) {
            logger.debug("JWT validation rejected a request");
            SecurityContextHolder.clearContext();
            if (!browserCookie) {
                reject(request, response, "Invalid bearer token");
                return;
            }
        } catch (Exception e) {
            logger.warn("JWT authentication failed");
            SecurityContextHolder.clearContext();
            if (!browserCookie) {
                reject(request, response, "Bearer token authentication failed");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAccountUsable(UserDetails userDetails) {
        return userDetails.isEnabled()
            && userDetails.isAccountNonLocked()
            && userDetails.isAccountNonExpired()
            && userDetails.isCredentialsNonExpired();
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (AuthCookieService.COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String traceId = RequestTraceFilter.ensureTraceId(request, response);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse error = ApiErrorResponse.builder()
            .timestamp(OffsetDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .code("INVALID_AUTH_TOKEN")
            .message(message)
            .fieldErrors(Map.of())
            .traceId(traceId)
            .build();
        objectMapper.writeValue(response.getWriter(), error);
    }
}
