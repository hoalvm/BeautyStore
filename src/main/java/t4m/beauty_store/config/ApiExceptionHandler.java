package t4m.beauty_store.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import t4m.beauty_store.auth.exception.OtpRateLimitException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Dữ liệu gửi lên chưa hợp lệ", fieldErrors, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiErrorResponse> badRequest(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION",
            exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(OtpRateLimitException.class)
    ResponseEntity<ApiErrorResponse> otpRateLimit(
            OtpRateLimitException exception, HttpServletRequest request) {
        return response(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
            exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(PublicApiRateLimitException.class)
    ResponseEntity<ApiErrorResponse> publicApiRateLimit(
            PublicApiRateLimitException exception, HttpServletRequest request) {
        return response(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintValidation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
            fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Dữ liệu gửi lên chưa hợp lệ", fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
            "Yêu cầu không đúng định dạng", Map.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> conflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
            "Dữ liệu bị trùng hoặc đang được tài nguyên khác sử dụng", Map.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> responseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, "HTTP_" + status.value(), message, Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        Object trace = request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE);
        log.error("Unhandled API error type={}, traceId={}",
            exception.getClass().getSimpleName(), trace);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Đã xảy ra lỗi hệ thống", Map.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
            Map<String, String> fields, HttpServletRequest request) {
        Object trace = request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE);
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
            .timestamp(OffsetDateTime.now())
            .status(status.value())
            .code(code)
            .message(message)
            .fieldErrors(fields)
            .traceId(trace == null ? null : trace.toString())
            .build());
    }
}
