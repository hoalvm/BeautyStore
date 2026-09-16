package t4m.beauty_store.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ATTRIBUTE = "beautystore.traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ensureTraceId(request, response);
        chain.doFilter(request, response);
    }

    public static String ensureTraceId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute(TRACE_ATTRIBUTE);
        String traceId = existing == null ? UUID.randomUUID().toString() : existing.toString();
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        response.setHeader("X-Trace-Id", traceId);
        return traceId;
    }
}
