package t4m.beauty_store.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single, injectable business clock for expiry, payment and policy calculations. */
@Component
public final class StoreTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;

    @Autowired
    public StoreTime(@Value("${store.time-zone:Asia/Ho_Chi_Minh}") String zoneId) {
        this(Clock.system(ZoneId.of(zoneId)));
    }

    public StoreTime(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime currentDateTime() {
        return LocalDateTime.now(clock);
    }

    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    /** @deprecated Inject {@link StoreTime} into business services instead. */
    @Deprecated(forRemoval = false)
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** @deprecated Inject {@link StoreTime} into business services instead. */
    @Deprecated(forRemoval = false)
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
