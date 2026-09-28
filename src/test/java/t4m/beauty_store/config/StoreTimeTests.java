package t4m.beauty_store.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StoreTimeTests {

    @Test
    void fixedClockUsesVietnamBusinessTime() {
        StoreTime storeTime = new StoreTime(
            Clock.fixed(Instant.parse("2026-06-15T17:30:00Z"), StoreTime.ZONE));

        assertThat(storeTime.currentDateTime())
            .isEqualTo(LocalDateTime.of(2026, 6, 16, 0, 30));
        assertThat(storeTime.currentDate()).isEqualTo(LocalDate.of(2026, 6, 16));
    }
}
