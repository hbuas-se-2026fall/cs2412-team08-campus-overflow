package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryVotes;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 投票用例编排测试：自投拒绝、幂等、改投 delta、撤销回退。 */
class VoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final InMemoryVotes votes = new InMemoryVotes();
    private final RecordingEvents events = new RecordingEvents();

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    private VoteService voteService;
    private long questionId;

    @BeforeEach
    void setUp() {
        voteService = new VoteService(votes, questions, answers, events, clock);
        Question question = questions.save(Question.post(ming.userId(), 100L, new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个反例。"), Tags.of(List.of("数据结构", "红黑树")), NOW));
        questionId = question.id();
    }

    @Test
    @DisplayName("不能给自己的内容投票，且不发布事件")
    void selfVoteRejected() {
        assertThatThrownBy(() -> voteService.cast(ming, TargetType.QUESTION, questionId, 1))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("QA-1015");
        assertThat(events.published).isEmpty();
    }

    @Test
    @DisplayName("同一值重复投票幂等：delta 为 0，不重复发布事件")
    void repeatSameVoteIsIdempotent() {
        voteService.cast(hong, TargetType.QUESTION, questionId, 1);

        VoteService.VoteResult second = voteService.cast(hong, TargetType.QUESTION, questionId, 1);

        assertThat(second.scoreDelta()).isZero();
        assertThat(events.ofType(VoteCastEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("加一票改投减一票：delta 为负二，问题分数回落")
    void changeUpToDownDeltaMinusTwo() {
        voteService.cast(hong, TargetType.QUESTION, questionId, 1);

        VoteService.VoteResult second = voteService.cast(hong, TargetType.QUESTION, questionId, -1);

        assertThat(second.scoreDelta()).isEqualTo(-2);
        assertThat(questions.scoreDeltas.get(questionId)).isEqualTo(-1);
    }

    @Test
    @DisplayName("撤销投票后分数回退到初始")
    void cancelVoteRollsScoreBack() {
        voteService.cast(hong, TargetType.QUESTION, questionId, 1);

        VoteService.VoteResult cancel = voteService.cast(hong, TargetType.QUESTION, questionId, 0);

        assertThat(cancel.scoreDelta()).isEqualTo(-1);
        assertThat(questions.scoreDeltas.get(questionId)).isZero();
    }

    @Test
    @DisplayName("对回答投票：回答分数增加并发布 ANSWER 事件")
    void voteOnAnswerBumpsAnswerScore() {
        Question question = questions.findById(questionId).orElseThrow();
        Answer answer = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量。"), NOW));

        VoteService.VoteResult result = voteService.cast(ming, TargetType.ANSWER, answer.id(), 1);

        assertThat(result.scoreDelta()).isEqualTo(1);
        assertThat(answers.scoreDeltas.get(answer.id())).isEqualTo(1);
        assertThat(events.ofType(VoteCastEvent.class).get(0).targetType()).isEqualTo("ANSWER");
    }

    @Test
    @DisplayName("对已删除问题投票返回 404，且不发布事件")
    void voteOnDeletedQuestionReturns404() {
        Question deleted = questions.findById(questionId).orElseThrow();
        deleted.softDelete(ming, false, NOW);
        questions.save(deleted);

        assertThatThrownBy(() -> voteService.cast(hong, TargetType.QUESTION, questionId, 1))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QA-4001");
        assertThat(events.published).isEmpty();
    }
}
