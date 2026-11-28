package com.campusoverflow.shared.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void keepsValidValues() {
        PageRequest request = new PageRequest(3, 20);
        assertThat(request.page()).isEqualTo(3);
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    void clampsPageToOne() {
        assertThat(new PageRequest(0, 20).page()).isEqualTo(1);
        assertThat(new PageRequest(-5, 20).page()).isEqualTo(1);
    }

    @Test
    void clampsSizeToOne() {
        assertThat(new PageRequest(1, 0).size()).isEqualTo(1);
        assertThat(new PageRequest(1, -10).size()).isEqualTo(1);
    }

    @Test
    void clampsSizeToMax() {
        assertThat(new PageRequest(1, 1000).size()).isEqualTo(PageRequest.MAX_SIZE);
        assertThat(PageRequest.MAX_SIZE).isEqualTo(50);
    }

    @Test
    void computesOffset() {
        assertThat(new PageRequest(1, 20).offset()).isZero();
        assertThat(new PageRequest(3, 20).offset()).isEqualTo(40);
        assertThat(new PageRequest(0, 20).offset()).isZero();
    }
}
