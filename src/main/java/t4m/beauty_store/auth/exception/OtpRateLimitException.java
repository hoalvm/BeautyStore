package t4m.beauty_store.auth.exception;

/** Raised when OTP issuance or verification exceeds the configured safety limits. */
public class OtpRateLimitException extends RuntimeException {

    public OtpRateLimitException(String message) {
        super(message);
    }
}
