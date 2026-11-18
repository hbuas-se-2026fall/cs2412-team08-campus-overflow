package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.ForbiddenException;
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

/** 采纳用例编排测试：权限入口、事件字段、审计与缓存失效。领域规则由 QuestionTest 覆盖。 */
class AnswerAcceptServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER).withCourseManager(9);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);
    private final Actor stranger = new Actor(77, "路人", Role.STUDENT);

    private AnswerCommandService answerService;
    private Question question;

    @BeforeEach
    void setUp() {
        answerService = new AnswerCommandService(questions, answers, identity, events, cache, audit, clock);
        question = questions.save(Question.post(ming.userId(), 100L, new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
    }

    private long saveAnswer() {
        Answer answer = Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量，仅靠变色无法调整。"), NOW);
        return answers.save(answer).id();
    }

    @Test
    @DisplayName("提问者采纳：保存聚合、发布 AnswerAccepted、写审计并失效列表缓存")
    void askerCanAccept() {
        long answerId = saveAnswer();

        answerService.accept(ming, question.id(), answerId);

        assertThat(questions.findById(question.id()).orElseThrow().acceptedAnswerId()).isEqualTo(answerId);
        List<AnswerAcceptedEvent> accepted = events.ofType(AnswerAcceptedEvent.class);
        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).answerId()).isEqualTo(answerId);
        assertThat(accepted.get(0).questionId()).isEqualTo(question.id());
        assertThat(accepted.get(0).answerAuthorId()).isEqualTo(hong.userId());
        assertThat(accepted.get(0).questionAuthorId()).isEqualTo(ming.userId());
        assertThat(accepted.get(0).acceptedById()).isEqualTo(ming.userId());
        assertThat(accepted.get(0).questionTitle()).isEqualTo(question.title().value());
        assertThat(audit.actions).contains("ANSWER_ACCEPTED");
        assertThat(cache.invalidations).isEqualTo(1);
    }

    @Test
    @DisplayName("课程管理者（教师/助教）可以采纳")
    void courseManagerCanAccept() {
        long answerId = saveAnswer();

        answerService.accept(teacher, question.id(), answerId);

        assertThat(questions.findById(question.id()).orElseThrow().acceptedAnswerId()).isEqualTo(answerId);
    }

    @Test
    @DisplayName("既非提问者也非课程管理者，不能采纳")
    void strangerCannotAccept() {
        long answerId = saveAnswer();

        assertThatThrownBy(() -> answerService.accept(stranger, question.id(), answerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("问题或回答不存在时返回 404")
    void missingTargetReturns404() {
        long answerId = saveAnswer();

        assertThatThrownBy(() -> answerService.accept(ming, 999L, answerId))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QA-4001");
        assertThatThrownBy(() -> answerService.accept(ming, question.id(), 999L))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QA-4002");
    }
}
