package t4m.beauty_store.config;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class ApiErrorResponse {
    private OffsetDateTime timestamp;
    private int status;
    private String code;
    private String message;
    private Map<String, String> fieldErrors;
    private String traceId;
}
