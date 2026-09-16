package t4m.beauty_store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.time.OffsetDateTime;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        // Check if it's an API request
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/")) {
            // Return JSON response for API requests
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String trace = RequestTraceFilter.ensureTraceId(request, response);
            ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .code("AUTHENTICATION_REQUIRED")
                .message("Bạn cần đăng nhập để truy cập tài nguyên này")
                .fieldErrors(Map.of())
                .traceId(trace)
                .build();
            response.getWriter().write(objectMapper.writeValueAsString(error));
        } else {
            // Redirect to login page for web requests
            response.sendRedirect("/login?error=unauthorized");
        }
    }
}
