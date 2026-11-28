package com.campusoverflow.shared.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void emptyHasNoItems() {
        PageResult<String> result = PageResult.empty(2, 20);
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void copiesItemsDefensively() {
        List<String> source = new ArrayList<>(List.of("a", "b"));
        PageResult<String> result = new PageResult<>(source, 2, 1, 2);
        source.add("c");
        assertThat(result.items()).containsExactly("a", "b");
    }

    @Test
    void mapKeepsPagingMetadata() {
        PageResult<Integer> mapped = new PageResult<>(List.of("a", "bb"), 7, 2, 2)
                .map(String::length);
        assertThat(mapped.items()).containsExactly(1, 2);
        assertThat(mapped.total()).isEqualTo(7);
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(2);
    }

    @Test
    void totalPagesRoundsUp() {
        assertThat(new PageResult<>(List.of(), 40, 1, 20).totalPages()).isEqualTo(2);
        assertThat(new PageResult<>(List.of(), 41, 1, 20).totalPages()).isEqualTo(3);
        assertThat(new PageResult<>(List.of(), 1, 1, 20).totalPages()).isEqualTo(1);
    }

    @Test
    void totalPagesIsZeroWhenSizeIsZero() {
        assertThat(new PageResult<>(List.of(), 10, 1, 0).totalPages()).isZero();
    }
}
