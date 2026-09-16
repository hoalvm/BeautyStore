package t4m.beauty_store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import t4m.beauty_store.auth.service.CustomUserDetailsService;
import t4m.beauty_store.auth.util.JwtRequestFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtRequestFilter jwtRequestFilter;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final String allowedOrigins;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
            JwtRequestFilter jwtRequestFilter,
            CustomAccessDeniedHandler accessDeniedHandler,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            @Value("${security.cors.allowed-origins:http://localhost:8080,http://localhost:3000}")
            String allowedOrigins) {
        this.userDetailsService = userDetailsService;
        this.jwtRequestFilter = jwtRequestFilter;
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public HTML pages (no authentication required for viewing)
                        .requestMatchers("/", "/home", "/index", "/login", "/register", "/forgot-password", "/reset-password",
                                "/verify-otp")
                        .permitAll()
                        .requestMatchers("/products", "/products/**", "/product/**").permitAll()
                        .requestMatchers("/cart", "/checkout", "/orders", "/favorites", "/profile").permitAll()
                        .requestMatchers("/order-confirmation", "/order-confirmation/**").permitAll() // Order confirmation after payment
                        .requestMatchers("/payment-pending", "/payment-pending/**").permitAll() // Payment pending page
                        .requestMatchers("/terms", "/privacy", "/return-policy", "/shopping-guide", "/payment-security").permitAll() // Policy pages
                        // Safe page navigation uses the short-lived HttpOnly JWT cookie.
                        // All mutating APIs still require the explicit bearer header.
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/shipper", "/shipper/**").hasRole("SHIPPER")

                        // Static resources
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico",
                                "/robots.txt", "/sitemap.xml", "/error")
                        .permitAll()

                        // Only authentication bootstrap/OTP operations are anonymous.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/active-account",
                                "/api/auth/verify-account",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/logout")
                        .permitAll()

                        // Public catalog is read-only. Legacy remote-image mutation stays admin-only.
                        .requestMatchers("/api/products/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/message").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/chatbot/health").permitAll()

                        // VNPay server callback and browser return must be reachable anonymously.
                        // Payment-link creation falls through to authenticated rules.
                        .requestMatchers(HttpMethod.GET,
                                "/api/payment/vnpay/return",
                                "/api/payment/vnpay/ipn")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/payment/vnpay/create-url/*",
                                "/api/payment/vnpay/create-payment-link")
                        .permitAll()

                        // Guest commerce endpoints use an opaque, HttpOnly guest cookie/token.
                        .requestMatchers("/api/cart/**", "/api/guest-orders/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders/checkout", "/api/vouchers/validate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ratings/product/*/summary").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/product/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reviews").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/uploads/review", "/api/uploads/return").permitAll()
                        // Return resources authorize the account or opaque guest-order token in service code.
                        .requestMatchers("/api/returns", "/api/returns/**").permitAll()

                        // Admin API - require ADMIN role (MUST BE BEFORE general /api/support/**)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/support/admin/**").hasRole("ADMIN")

                        // The service authorizes member ownership or the opaque HttpOnly
                        // guest identity before exposing a support conversation.
                        .requestMatchers("/api/support/session", "/api/support/session/**").permitAll()

                        // Protected API endpoints - require authentication
                        .requestMatchers("/api/checkout/**").authenticated()
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/payment/vnpay/**").authenticated()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/ratings/**").authenticated()
                        .requestMatchers("/api/favorites/**").authenticated()
                        .requestMatchers("/api/user/**").authenticated()
                        .requestMatchers("/api/support/**").authenticated()

                        // Vendor API endpoints
                        .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                        // Shipper API endpoints
                        .requestMatchers("/api/shipper/**").hasRole("SHIPPER")

                        // Fail closed for new routes until their access policy is explicit.
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Order-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
