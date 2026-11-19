package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.application.QaQueryApiImpl;
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
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.DomainException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 回答编辑、删除、教师认证与跨模块查询的编排测试。 */
class AnswerEditingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT)
            .withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER)
            .withCourseManager(9);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    private AnswerCommandService service;
    private Question question;
    private Answer answer;
    private long answerId;

    @BeforeEach
    void setUp() {
        service = new AnswerCommandService(questions, answers, identity, events, cache, audit, clock);
        question = questions.save(Question.post(ming.userId(), 100L, new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        answer = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("因为插入红色节点会破坏黑高约束，所以必须旋转恢复"), NOW));
        answerId = answer.id();
    }

    @Test
    @DisplayName("编辑回答：作者本人可以改，正文更新并留审计")
    void editByAuthor() {
        service.edit(hong, answerId, "因为旋转是为了维持黑高约束，否则查找会退化成链表");

        assertThat(answers.findById(answerId).orElseThrow().body().value())
                .isEqualTo("因为旋转是为了维持黑高约束，否则查找会退化成链表");
        assertThat(audit.actions).contains("ANSWER_EDITED");
    }

    @Test
    @DisplayName("编辑回答：非作者且非管理者被拒绝 QA-3002")
    void editByOtherStudent() {
        assertThatThrownBy(() -> service.edit(ming, answerId, "这里换成一段足够长的正文用于触发权限校验"))
                .isInstanceOf(ForbiddenException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("QA-3002");
    }

    @Test
    @DisplayName("编辑回答：课程管理者可以改他人回答")
    void editByCourseManager() {
        service.edit(teacher, answerId, "教师补充说明：旋转的本质是把红节点上移并重新着色");

        assertThat(answers.findById(answerId).orElseThrow().body().value())
                .isEqualTo("教师补充说明：旋转的本质是把红节点上移并重新着色");
    }

    @Test
    @DisplayName("删除回答：作者删除后标记为已删除，发事件并清缓存")
    void deleteByAuthor() {
        service.delete(hong, answerId);

        assertThat(answers.findById(answerId).orElseThrow().isDeleted()).isTrue();
        assertThat(events.ofType(ContentDeletedEvent.class)).hasSize(1);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).contains("ANSWER_DELETED");
    }

    @Test
    @DisplayName("删除回答：已采纳的回答不能由作者自己删除 QA-1012")
    void deleteAcceptedAnswerByAuthor() {
        question.accept(answer, ming, false);
        questions.save(question);

        assertThatThrownBy(() -> service.delete(hong, answerId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("QA-1012");
    }

    @Test
    @DisplayName("删除回答：课程管理者可以删除已采纳的回答")
    void deleteAcceptedAnswerByManager() {
        question.accept(answer, ming, false);
        questions.save(question);

        service.delete(teacher, answerId);

        assertThat(answers.findById(answerId).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("教师认证：管理者认证成功，记录认证人并发事件")
    void endorseByTeacher() {
        service.endorse(teacher, answerId);

        Answer saved = answers.findById(answerId).orElseThrow();
        assertThat(saved.endorsedBy()).isEqualTo(teacher.userId());
        AnswerEndorsedEvent event = events.ofType(AnswerEndorsedEvent.class).get(0);
        assertThat(event.answerId()).isEqualTo(answerId);
        assertThat(event.answerAuthorId()).isEqualTo(hong.userId());
        assertThat(event.endorserId()).isEqualTo(teacher.userId());
    }

    @Test
    @DisplayName("教师认证：学生认证被拒绝 QA-3004")
    void endorseByStudent() {
        assertThatThrownBy(() -> service.endorse(ming, answerId))
                .isInstanceOf(ForbiddenException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("QA-3004");
    }

    @Test
    @DisplayName("教师认证：重复认证冲突 QA-2003")
    void endorseTwice() {
        service.endorse(teacher, answerId);

        assertThatThrownBy(() -> service.endorse(teacher, answerId))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("QA-2003");
    }

    @Test
    @DisplayName("跨模块查询：返回问题引用，未删除标记正确，不存在返回空")
    void findQuestionReturnsRef() {
        QaQueryApiImpl api = new QaQueryApiImpl(questions);

        Optional<QuestionRef> ref = api.findQuestion(question.id());

        assertThat(ref).isPresent();
        assertThat(ref.orElseThrow().title()).isEqualTo("红黑树插入后为什么必须旋转？");
        assertThat(ref.orElseThrow().authorId()).isEqualTo(ming.userId());
        assertThat(ref.orElseThrow().deleted()).isFalse();
        assertThat(api.findQuestion(999L)).isEmpty();
    }
}
