package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AggregateLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-02T09:30:00Z");
    private static final Actor ANSWERER = new Actor(3, "回答者", Role.STUDENT);
    private static final Actor STRANGER = new Actor(77, "路人", Role.STUDENT);

    @Test
    void reconstitutedQuestionExposesEveryPersistedField() {
        Question q = Question.reconstitute(10L, 1, 100L, new Title("重构后的红黑树旋转规则问题标题"),
                MarkdownBody.ofQuestion("从数据库重建的问题正文，用于校验聚合根的只读访问器是否完整。"),
                Tags.of(List.of("数据结构", "红黑树")), 50L, 7, 3, 120, NOW, LATER, null, 4);

        assertThat(q.id()).isEqualTo(10L);
        assertThat(q.authorId()).isEqualTo(1L);
        assertThat(q.courseId()).isEqualTo(100L);
        assertThat(q.title().value()).isEqualTo("重构后的红黑树旋转规则问题标题");
        assertThat(q.body().value()).contains("只读访问器");
        assertThat(q.tags().names()).containsExactly("数据结构", "红黑树");
        assertThat(q.acceptedAnswerId()).isEqualTo(50L);
        assertThat(q.score()).isEqualTo(7);
        assertThat(q.answerCount()).isEqualTo(3);
        assertThat(q.viewCount()).isEqualTo(120);
        assertThat(q.createdAt()).isEqualTo(NOW);
        assertThat(q.updatedAt()).isEqualTo(LATER);
        assertThat(q.deletedAt()).isNull();
        assertThat(q.version()).isEqualTo(4L);
        assertThat(q.isAccepted(50)).isTrue();
        assertThat(q.isAccepted(51)).isFalse();
        assertThat(q.isDeleted()).isFalse();
    }

    @Test
    void strangerCannotDeleteQuestionAndIdCanOnlyBeAssignedOnce() {
        Question q = QuestionTest.persistedQuestion();
        assertThatThrownBy(() -> q.softDelete(STRANGER, false, NOW))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("删除");
        assertThatThrownBy(() -> q.assignId(11)).isInstanceOf(IllegalStateException.class);
        assertThat(q.id()).isEqualTo(10L);
    }

    @Test
    void reconstitutedAnswerExposesEveryPersistedField() {
        Answer a = Answer.reconstitute(50L, 10L, 3, MarkdownBody.ofAnswer("看叔叔节点的颜色和插入位置再决定旋转方向。"),
                5, 9L, NOW, LATER, null, 2);

        assertThat(a.id()).isEqualTo(50L);
        assertThat(a.questionId()).isEqualTo(10L);
        assertThat(a.authorId()).isEqualTo(3L);
        assertThat(a.body().value()).contains("叔叔节点");
        assertThat(a.score()).isEqualTo(5);
        assertThat(a.endorsedBy()).isEqualTo(9L);
        assertThat(a.createdAt()).isEqualTo(NOW);
        assertThat(a.updatedAt()).isEqualTo(LATER);
        assertThat(a.deletedAt()).isNull();
        assertThat(a.version()).isEqualTo(2L);
        assertThat(a.isDeleted()).isFalse();
    }

    @Test
    void authorEditsAnswerAndTimestampAdvances() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, ANSWERER.userId(), 50);
        a.edit(ANSWERER, false, MarkdownBody.ofAnswer("改成先看叔叔节点颜色，再按插入位置选择左旋或右旋。"), LATER);
        assertThat(a.body().value()).contains("左旋或右旋");
        assertThat(a.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void deletedAnswerRejectsFurtherEdits() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, ANSWERER.userId(), 50);
        a.softDelete(ANSWERER, false, false, LATER);
        assertThatThrownBy(() -> a.edit(ANSWERER, false, MarkdownBody.ofAnswer("删除之后还想再改一次内容的回答。"), LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已删除");
    }

    @Test
    void answerIdCannotBeAssignedTwice() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, ANSWERER.userId(), 50);
        assertThatThrownBy(() -> a.assignId(51)).isInstanceOf(IllegalStateException.class);
        assertThat(a.id()).isEqualTo(50L);
    }

    @Test
    void reconstitutedVoteExposesEveryPersistedField() {
        Vote v = Vote.reconstitute(7L, 1, TargetType.ANSWER, 50, -1, LATER);
        assertThat(v.id()).isEqualTo(7L);
        assertThat(v.voterId()).isEqualTo(1L);
        assertThat(v.targetType()).isEqualTo(TargetType.ANSWER);
        assertThat(v.targetId()).isEqualTo(50L);
        assertThat(v.value()).isEqualTo(-1);
        assertThat(v.updatedAt()).isEqualTo(LATER);

        Vote pending = Vote.cast(1, 3, TargetType.QUESTION, 10, 1, NOW);
        assertThat(pending.id()).isNull();
        pending.assignId(8);
        assertThat(pending.id()).isEqualTo(8L);
    }

    @Test
    void reconstitutedCommentExposesEveryPersistedField() {
        Comment c = Comment.reconstitute(2L, TargetType.QUESTION, 10, 10, 3, 1L, "因为要保持黑高一致。", LATER);
        assertThat(c.id()).isEqualTo(2L);
        assertThat(c.targetType()).isEqualTo(TargetType.QUESTION);
        assertThat(c.targetId()).isEqualTo(10L);
        assertThat(c.questionId()).isEqualTo(10L);
        assertThat(c.authorId()).isEqualTo(3L);
        assertThat(c.parentId()).isEqualTo(1L);
        assertThat(c.body()).isEqualTo("因为要保持黑高一致。");
        assertThat(c.createdAt()).isEqualTo(LATER);

        assertThatThrownBy(() -> c.assignId(3)).isInstanceOf(IllegalStateException.class);
        assertThat(c.id()).isEqualTo(2L);
    }
}
