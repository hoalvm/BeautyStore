package t4m.beauty_store.config;

public class PublicApiRateLimitException extends RuntimeException {
    public PublicApiRateLimitException(String message) {
        super(message);
    }
}
