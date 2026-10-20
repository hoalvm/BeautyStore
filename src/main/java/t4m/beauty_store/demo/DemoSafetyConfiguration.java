package t4m.beauty_store.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** Demo credentials/mail previews must never become a remotely exposed deployment. */
@Configuration
@Profile("demo")
public class DemoSafetyConfiguration {
    @Bean
    InitializingBean validateDemoIsolation(Environment environment) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of("prod", "dev"))
                    || !"127.0.0.1".equals(environment.getProperty("server.address"))
                    || !environment.getProperty("spring.datasource.url", "").startsWith("jdbc:h2:")) {
                throw new IllegalStateException("Demo must run alone on 127.0.0.1 with H2. Select dev/prod for external deployment.");
            }
        };
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> demoLoopbackOnly() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                // Do not trust forwarded headers; disallow DNS rebinding to a local mailbox.
                boolean loopback = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
                boolean host = Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(request.getServerName());
                if (!loopback || !host) {
                    response.sendError(403, "Local demo is available on localhost only");
                    return;
                }
                response.setHeader("Cache-Control", "no-store");
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
