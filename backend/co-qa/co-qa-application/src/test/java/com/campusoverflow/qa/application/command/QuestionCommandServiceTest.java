package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.TagRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 应用服务用例测试：验证发布/编辑/删除问题的编排逻辑（权限、事件、标签计数、缓存、审计）。 */
class QuestionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final FakeTagRepository tags = new FakeTagRepository();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(9, "王老师", Role.TEACHER);
    private final FakeRenderer renderer = new FakeRenderer();

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor stranger = new Actor(77, "路人", Role.STUDENT);

    private QuestionCommandService service;

    @BeforeEach
    void setUp() {
        service = new QuestionCommandService(questions, tags, identity, events, renderer, cache, audit, clock);
    }

    private PostQuestionCommand postCommand() {
        return new PostQuestionCommand(100L, "红黑树插入后如何判断旋转方向？",
                "插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？", List.of("数据结构", "红黑树"));
    }

    @Test
    @DisplayName("发布问题：保存聚合、标签计数+1、发布事件、失效缓存、写审计")
    void postPublishesEventAndAdjustsTags() {
        long id = service.post(ming, postCommand());

        Question saved = questions.findById(id).orElseThrow();
        assertThat(saved.authorId()).isEqualTo(1);
        assertThat(saved.courseId()).isEqualTo(100L);
        assertThat(saved.title().value()).contains("旋转方向");
        assertThat(saved.tags().names()).containsExactly("数据结构", "红黑树");
        assertThat(tags.usages).containsEntry("数据结构", 1).containsEntry("红黑树", 1);

        List<QuestionPostedEvent> posted = events.ofType(QuestionPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).questionId()).isEqualTo(id);
        assertThat(posted.get(0).authorId()).isEqualTo(ming.userId());
        assertThat(posted.get(0).title()).contains("旋转方向");
        assertThat(posted.get(0).plainText()).isEqualTo("渲染纯文本");
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).contains("QUESTION_POSTED");
    }

    @Test
    @DisplayName("发布问题：标题过短抛出业务异常")
    void postRejectsShortTitle() {
        assertThatThrownBy(() -> service.post(ming,
                new PostQuestionCommand(100L, "太短", "正文长度没有问题的一段话，足够长。", List.of("java"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("编辑问题：作者可编辑，标签差集分别计数，发布事件并失效缓存")
    void editByAuthorUpdatesTagsAndPublishes() {
        long id = service.post(ming, postCommand());
        service.edit(ming, id, new EditQuestionCommand("红黑树插入后的旋转规则是什么？",
                "插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？", List.of("数据结构", "算法")));

        Question updated = questions.findById(id).orElseThrow();
        assertThat(updated.title().value()).contains("旋转规则");
        assertThat(updated.tags().names()).containsExactly("数据结构", "算法");
        assertThat(tags.usages).containsEntry("红黑树", 0).containsEntry("算法", 1).containsEntry("数据结构", 1);

        List<QuestionEditedEvent> edited = events.ofType(QuestionEditedEvent.class);
        assertThat(edited).hasSize(1);
        assertThat(cache.invalidations).isEqualTo(2);
        assertThat(audit.actions).contains("QUESTION_EDITED");
    }

    @Test
    @DisplayName("编辑问题：路人无权编辑")
    void editByStrangerIsForbidden() {
        long id = service.post(ming, postCommand());
        assertThatThrownBy(() -> service.edit(stranger, id,
                new EditQuestionCommand("改标题啊改标题啊改标题啊", "改正文改正文改正文改正文改正文改正文改正文", List.of("java"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("删除问题：作者软删除、标签计数-1、发布事件并审计")
    void deleteByAuthorSoftDeletes() {
        long id = service.post(ming, postCommand());
        service.delete(ming, id);

        Question deleted = questions.findById(id).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(tags.usages).containsEntry("数据结构", 0).containsEntry("红黑树", 0);

        List<ContentDeletedEvent> deletedEvents = events.ofType(ContentDeletedEvent.class);
        assertThat(deletedEvents).hasSize(1);
        assertThat(deletedEvents.get(0).targetType()).isEqualTo("QUESTION");
        assertThat(deletedEvents.get(0).targetId()).isEqualTo(id);
        assertThat(audit.actions).contains("QUESTION_DELETED");
        assertThat(cache.invalidations).isEqualTo(2);
    }

    @Test
    @DisplayName("加载问题：不存在或已删除统一返回404")
    void loadReturns404WhenMissingOrDeleted() {
        assertThatThrownBy(() -> service.delete(ming, 999L)).isInstanceOf(NotFoundException.class);
        long id = service.post(ming, postCommand());
        service.delete(ming, id);
        assertThatThrownBy(() -> service.edit(ming, id,
                new EditQuestionCommand("改标题啊改标题啊改标题啊", "改正文改正文改正文改正文改正文改正文改正文", List.of("java"))))
                .isInstanceOf(NotFoundException.class);
    }

    private static final class FakeTagRepository implements TagRepository {
        private final Map<String, Integer> usages = new HashMap<>();

        @Override
        public void adjustUsage(Collection<String> tagNames, int delta) {
            tagNames.forEach(name -> usages.merge(name, delta, Integer::sum));
        }
    }

    private static final class FakeRenderer implements ContentRenderer {
        @Override
        public String toSafeHtml(String markdown) {
            return "<p>" + markdown + "</p>";
        }

        @Override
        public String toPlainText(String markdown, int maxLength) {
            return "渲染纯文本";
        }
    }
}
