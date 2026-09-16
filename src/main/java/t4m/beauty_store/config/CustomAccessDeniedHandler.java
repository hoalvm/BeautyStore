package t4m.beauty_store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.time.OffsetDateTime;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        // Check if it's an API request
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/")) {
            // Return JSON response for API requests
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String trace = RequestTraceFilter.ensureTraceId(request, response);
            ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpServletResponse.SC_FORBIDDEN)
                .code("ACCESS_DENIED")
                .message("Bạn không có quyền truy cập vào tài nguyên này")
                .fieldErrors(Map.of())
                .traceId(trace)
                .build();

            response.getWriter().write(objectMapper.writeValueAsString(error));
        } else {
            // For HTML pages (including /admin/**), redirect to login with error
            response.sendRedirect("/login?error=access_denied");
        }
    }
}
