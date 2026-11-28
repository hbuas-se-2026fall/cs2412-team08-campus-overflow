package com.campusoverflow.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GuardTest {

    @Test
    void requireTextRejectsNull() {
        assertThatThrownBy(() -> Guard.requireText(null, 2, 10, "QA-4001", "标题"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("标题不能为空")
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-4001"));
    }

    @Test
    void requireTextRejectsBlank() {
        assertThatThrownBy(() -> Guard.requireText("   ", 2, 10, "QA-4001", "标题"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("标题不能为空");
    }

    @Test
    void requireTextRejectsTooShort() {
        assertThatThrownBy(() -> Guard.requireText("a", 2, 10, "QA-4002", "标题"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("长度应在 2–10")
                .hasMessageContaining("当前 1");
    }

    @Test
    void requireTextRejectsTooLong() {
        assertThatThrownBy(() -> Guard.requireText("abcdefghijk", 2, 10, "QA-4002", "标题"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("当前 11");
    }

    @Test
    void requireTextStripsSurroundingWhitespace() {
        assertThat(Guard.requireText("  红黑树实现  ", 2, 10, "QA-4002", "标题"))
                .isEqualTo("红黑树实现");
    }

    @Test
    void requireTextCountsCodePointsNotChars() {
        String emoji = "😀😀";
        assertThat(emoji.length()).isEqualTo(4);
        assertThat(Guard.requireText(emoji, 2, 2, "QA-4002", "标题")).isEqualTo(emoji);
    }

    @Test
    void requireTextAcceptsBoundaryLengths() {
        assertThat(Guard.requireText("ab", 2, 10, "QA-4002", "标题")).isEqualTo("ab");
        assertThat(Guard.requireText("abcdefghij", 2, 10, "QA-4002", "标题")).isEqualTo("abcdefghij");
    }

    @Test
    void requireNonNullRejectsNull() {
        assertThatThrownBy(() -> Guard.requireNonNull(null, "QA-4003", "调用者"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("调用者不能为空")
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-4003"));
    }

    @Test
    void requireNonNullReturnsValue() {
        assertThat(Guard.requireNonNull(42L, "QA-4003", "调用者")).isEqualTo(42L);
    }
}
