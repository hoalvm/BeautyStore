package t4m.beauty_store.auth.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import t4m.beauty_store.auth.service.AuthCookieService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtRequestFilterTests {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtRequestFilter filter = new JwtRequestFilter(
        jwtUtil, userDetailsService, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingTokenIsDelegatedToSpringSecurityAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsUnsupportedAuthorizationScheme() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Basic unsafe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_AUTH_TOKEN\"")
            .contains("\"traceId\":");
        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
    }

    @Test
    void authenticatesValidBearerTokenWithoutTrustingIdentityHeaders() throws Exception {
        UserDetails user = User.withUsername("customer@example.com")
                .password("unused")
                .roles("USER")
                .build();
        when(jwtUtil.extractUsername("signed-token")).thenReturn("customer@example.com");
        when(userDetailsService.loadUserByUsername("customer@example.com")).thenReturn(user);
        when(jwtUtil.isTokenValid("signed-token", "customer@example.com")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/profile");
        request.addHeader("Authorization", "Bearer signed-token");
        request.addHeader("X-User-Email", "attacker@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("customer@example.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatesSafePageNavigationFromHttpOnlyCookie() throws Exception {
        UserDetails admin = User.withUsername("admin@example.com")
                .password("unused")
                .roles("ADMIN")
                .build();
        when(jwtUtil.extractUsername("page-token")).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(admin);
        when(jwtUtil.isTokenValid("page-token", "admin@example.com")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME, "page-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("admin@example.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsValidJwtWhenCurrentUserIsDisabled() throws Exception {
        UserDetails disabledUser = User.withUsername("disabled@example.com")
                .password("unused")
                .roles("USER")
                .disabled(true)
                .build();
        when(jwtUtil.extractUsername("still-signed-token")).thenReturn("disabled@example.com");
        when(userDetailsService.loadUserByUsername("disabled@example.com"))
                .thenReturn(disabledUser);
        when(jwtUtil.isTokenValid("still-signed-token", "disabled@example.com"))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer still-signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_AUTH_TOKEN\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doesNotUseAmbientAuthCookieForMutation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/products");
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME, "page-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
