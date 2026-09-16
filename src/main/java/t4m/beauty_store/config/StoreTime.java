package t4m.beauty_store.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single business clock for expiry, payment and policy calculations. */
public final class StoreTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private StoreTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
