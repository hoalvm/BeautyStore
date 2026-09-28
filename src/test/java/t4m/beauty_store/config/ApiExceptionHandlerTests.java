package t4m.beauty_store.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTests {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainExceptionToStableStatusAndCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestTraceFilter.TRACE_ATTRIBUTE, "trace-123");

        var response = handler.domainError(ApiException.conflict("Đang được sử dụng"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_CONFLICT");
        assertThat(response.getBody().getTraceId()).isEqualTo("trace-123");
    }

    @Test
    void treatsUnexpectedStateAsInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.unexpected(new IllegalStateException("database secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
    }
}
