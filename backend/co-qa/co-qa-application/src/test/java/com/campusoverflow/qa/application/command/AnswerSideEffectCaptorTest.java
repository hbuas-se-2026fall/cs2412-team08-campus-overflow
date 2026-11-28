package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnswerSideEffectCaptorTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final long COURSE_ID = 100L;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final CountingCache cache = new CountingCache();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER).withCourseManager(9);
    private final IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
    private final AuditTrail audit = mock(AuditTrail.class);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    private AnswerCommandService service;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new AnswerCommandService(questions, answers, identity, publisher, cache, audit, clock);
        question = questions.save(Question.post(ming.userId(), COURSE_ID,
                new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个具体反例。"),
                Tags.of(List.of("数据结构")), NOW));
    }

    @Test
    @DisplayName("提交回答：ArgumentCaptor 断言事件字段与审计记录")
    void submitPublishesEventAndAudits() {
        long answerId = service.submit(hong, question.id(), "关键在于黑高这个不变量。");

        ArgumentCaptor<AnswerSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(AnswerSubmittedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        AnswerSubmittedEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.answerId()).isEqualTo(answerId);
        assertThat(event.questionId()).isEqualTo(question.id());
        assertThat(event.answerAuthorId()).isEqualTo(hong.userId());
        assertThat(event.questionAuthorId()).isEqualTo(ming.userId());
        assertThat(event.questionTitle()).isEqualTo(question.title().value());

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> actorId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> targetType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> targetId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(action.capture(), actorId.capture(), targetType.capture(), targetId.capture(),
                detail.capture());
        assertThat(action.getValue()).isEqualTo("ANSWER_SUBMITTED");
        assertThat(actorId.getValue()).isEqualTo(hong.userId());
        assertThat(targetType.getValue()).isEqualTo("ANSWER");
        assertThat(targetId.getValue()).isEqualTo(String.valueOf(answerId));
        assertThat(detail.getValue()).isNull();
        assertThat(cache.invalidations).isEqualTo(1);
        verifyNoMoreInteractions(publisher, audit);
    }

    @Test
    @DisplayName("采纳回答：ArgumentCaptor 断言事件与审计，且审计先于缓存失效")
    void acceptPublishesEventAndAudits() {
        long answerId = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("黑高是核心不变量，只靠变色调整不了。"), NOW)).id();

        service.accept(ming, question.id(), answerId);

        ArgumentCaptor<AnswerAcceptedEvent> eventCaptor = ArgumentCaptor.forClass(AnswerAcceptedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        AnswerAcceptedEvent event = eventCaptor.getValue();
        assertThat(event.answerId()).isEqualTo(answerId);
        assertThat(event.questionId()).isEqualTo(question.id());
        assertThat(event.answerAuthorId()).isEqualTo(hong.userId());
        assertThat(event.questionAuthorId()).isEqualTo(ming.userId());
        assertThat(event.acceptedById()).isEqualTo(ming.userId());
        assertThat(event.questionTitle()).isEqualTo(question.title().value());

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> actorId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> targetType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> targetId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(action.capture(), actorId.capture(), targetType.capture(), targetId.capture(),
                detail.capture());
        assertThat(action.getAllValues()).containsExactly("ANSWER_ACCEPTED");
        assertThat(actorId.getValue()).isEqualTo(ming.userId());
        assertThat(targetType.getValue()).isEqualTo("ANSWER");
        assertThat(targetId.getValue()).isEqualTo(String.valueOf(answerId));
        assertThat(detail.getValue()).isNull();
        assertThat(cache.invalidations).isEqualTo(1);
        verifyNoMoreInteractions(publisher, audit);
        assertThat(questions.findById(question.id()).orElseThrow().acceptedAnswerId()).isEqualTo(answerId);
    }

    @Test
    @DisplayName("教师认证：事件与审计同时写入，认证不触发问题列表缓存失效")
    void endorsePublishesEventAndAudits() {
        long answerId = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("黑高是核心不变量，只靠变色调整不了。"), NOW)).id();

        service.endorse(teacher, answerId);

        ArgumentCaptor<AnswerEndorsedEvent> eventCaptor = ArgumentCaptor.forClass(AnswerEndorsedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        AnswerEndorsedEvent event = eventCaptor.getValue();
        assertThat(event.answerId()).isEqualTo(answerId);
        assertThat(event.questionId()).isEqualTo(question.id());
        assertThat(event.answerAuthorId()).isEqualTo(hong.userId());
        assertThat(event.endorserId()).isEqualTo(teacher.userId());

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> actorId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> targetType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> targetId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(action.capture(), actorId.capture(), targetType.capture(), targetId.capture(),
                detail.capture());
        assertThat(action.getValue()).isEqualTo("ANSWER_ENDORSED");
        assertThat(actorId.getValue()).isEqualTo(teacher.userId());
        assertThat(targetType.getValue()).isEqualTo("ANSWER");
        assertThat(targetId.getValue()).isEqualTo(String.valueOf(answerId));
        assertThat(detail.getValue()).isNull();
        assertThat(cache.invalidations).isZero();
        verifyNoMoreInteractions(publisher, audit);
    }
}
