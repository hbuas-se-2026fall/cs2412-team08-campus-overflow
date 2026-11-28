package com.campusoverflow.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventIdsTest {

    @Test
    void nextProducesParseableUuid() {
        String id = EventIds.next();
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id)).hasToString(id);
    }

    @Test
    void nextIsUniquePerCall() {
        assertThat(EventIds.next()).isNotEqualTo(EventIds.next());
    }
}
