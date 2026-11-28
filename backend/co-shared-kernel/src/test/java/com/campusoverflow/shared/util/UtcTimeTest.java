package com.campusoverflow.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UtcTimeTest {

    @Test
    void toDbReturnsUtcWallClock() {
        Instant instant = Instant.parse("2026-01-01T08:30:00Z");
        assertThat(UtcTime.toDb(instant)).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 30));
    }

    @Test
    void toDbHandlesNull() {
        assertThat(UtcTime.toDb(null)).isNull();
    }

    @Test
    void fromDbReadsUtcWallClock() {
        LocalDateTime value = LocalDateTime.of(2026, 1, 1, 8, 30, 15, 123000000);
        assertThat(UtcTime.fromDb(value)).isEqualTo(value.toInstant(ZoneOffset.UTC));
    }

    @Test
    void fromDbHandlesNull() {
        assertThat(UtcTime.fromDb(null)).isNull();
    }

    @Test
    void roundTripsThroughDatabaseRepresentation() {
        Instant instant = Instant.parse("2026-03-08T04:12:33.456Z");
        assertThat(UtcTime.fromDb(UtcTime.toDb(instant))).isEqualTo(instant);
    }
}
