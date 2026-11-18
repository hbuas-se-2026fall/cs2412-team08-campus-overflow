package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryReadModel;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubContentRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 问答读路径测试：热门标签边界、列表摘要与缓存、详情组装与权限。 */
class QuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final PageRequest PAGE = new PageRequest(1, 10);

    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private final CountingCache cache = new CountingCache();
    private final StubViewCounter viewCounter = new StubViewCounter();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT)
            .withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER)
            .withCourseManager(9);
    private QuestionQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new QuestionQueryService(readModel, identity, new StubContentRenderer(), viewCounter, cache);
    }

    @Test
    @DisplayName("热门标签：原样转发读模型结果")
    void delegatesToReadModel() {
        readModel.tags = List.of(new TagView("java", 12L), new TagView("红黑树", 8L));

        List<TagView> result = queryService.popularTags(20);

        assertThat(result).containsExactlyElementsOf(readModel.tags);
        assertThat(readModel.lastPopularLimit).isEqualTo(20);
    }

    @Test
    @DisplayName("热门标签：limit 裁剪到 1–50")
    void clampsLimit() {
        queryService.popularTags(0);
        assertThat(readModel.lastPopularLimit).isEqualTo(1);
        queryService.popularTags(-5);
        assertThat(readModel.lastPopularLimit).isEqualTo(1);
        queryService.popularTags(999);
        assertThat(readModel.lastPopularLimit).isEqualTo(50);
    }

    @Test
    @DisplayName("列表：行转换为摘要视图并写缓存")
    void listMapsRowsAndCaches() {
        readModel.questions = new PageResult<>(List.of(
                row(7, 1, "红黑树插入后为什么必须旋转？", List.of("红黑树"), null),
                row(8, 2, "跳表怎么保证查询 O(log n)？", List.of("跳表"), 9L)), 2, 1, 10);

        PageResult<QuestionSummaryView> page = queryService.list(100L, PAGE);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        QuestionSummaryView first = page.items().get(0);
        assertThat(first.title()).isEqualTo("红黑树插入后为什么必须旋转？");
        assertThat(first.excerpt()).isEqualTo("红黑树插入后为什么必须旋转？");
        assertThat(first.author().displayName()).isEqualTo("小明");
        assertThat(first.answerCount()).isEqualTo(2);
        assertThat(first.accepted()).isFalse();
        assertThat(page.items().get(1).accepted()).isTrue();
        assertThat(cache.puts).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：第二次请求命中缓存，不回源也不重复写缓存")
    void listHitsCache() {
        readModel.questions = new PageResult<>(List.of(row(7, 1, "标题", List.of(), null)), 1, 1, 10);
        queryService.list(100L, PAGE);

        readModel.questions = new PageResult<>(List.of(
                row(7, 1, "标题", List.of(), null), row(8, 2, "另一个标题", List.of(), null)), 2, 1, 10);
        PageResult<QuestionSummaryView> second = queryService.list(100L, PAGE);

        assertThat(second.items()).hasSize(1);
        assertThat(cache.puts).isEqualTo(1);
    }

    @Test
    @DisplayName("详情：组装问题、回答、评论、我的投票与权限")
    void detailAssemblesView() {
        readModel.question = new QuestionRow(7, 1, 100L, "红黑树插入后为什么必须旋转？", "变色为什么不够",
                List.of("红黑树"), 9L, 5, 2, 42, NOW, NOW);
        readModel.answers = List.of(
                new AnswerRow(9, 2, "因为会破坏黑高", 6, 9L, NOW, NOW),
                new AnswerRow(10, 2, "补充一个反例", 2, null, NOW, NOW));
        readModel.comments = List.of(
                new CommentRow(1, "QUESTION", 7, 1, null, "补充一下：父子同为红也不行", NOW),
                new CommentRow(2, "ANSWER", 9, 1, 1L, "谢谢", NOW));
        readModel.votes = Map.of("QUESTION:7", 1, "ANSWER:9", -1);

        QuestionDetailView view = queryService.detail(7, new Actor(2, "小红", Role.STUDENT));

        assertThat(view.id()).isEqualTo(7);
        assertThat(view.bodyHtml()).isEqualTo("变色为什么不够");
        assertThat(view.course().code()).isEqualTo("CS2001");
        assertThat(view.tags()).containsExactly("红黑树");
        assertThat(view.myVote()).isEqualTo(1);
        assertThat(view.comments()).hasSize(1);
        assertThat(view.answers()).hasSize(2);

        AnswerView acceptedAnswer = view.answers().get(0);
        assertThat(acceptedAnswer.accepted()).isTrue();
        assertThat(acceptedAnswer.myVote()).isEqualTo(-1);
        assertThat(acceptedAnswer.endorsedBy().displayName()).isEqualTo("王老师");
        assertThat(acceptedAnswer.canEdit()).isTrue();
        assertThat(acceptedAnswer.comments()).hasSize(1);
        assertThat(view.answers().get(1).accepted()).isFalse();
        assertThat(view.permissions().canEdit()).isFalse();
        assertThat(view.permissions().canAccept()).isFalse();
        assertThat(view.permissions().canManage()).isFalse();
        assertThat(viewCounter.incremented).containsExactly(7L);
    }

    @Test
    @DisplayName("详情：提问者本人可编辑可采纳，课程管理者拥有管理权限")
    void detailPermissionsForAuthorAndManager() {
        readModel.question = new QuestionRow(7, 1, 100L, "标题", "正文", List.of(), null, 0, 0, 0, NOW, NOW);

        QuestionDetailView asAuthor = queryService.detail(7, new Actor(1, "小明", Role.STUDENT));
        assertThat(asAuthor.permissions().canEdit()).isTrue();
        assertThat(asAuthor.permissions().canAccept()).isTrue();
        assertThat(asAuthor.permissions().canManage()).isFalse();

        QuestionDetailView asManager = queryService.detail(7, new Actor(9, "王老师", Role.TEACHER));
        assertThat(asManager.permissions().canEdit()).isTrue();
        assertThat(asManager.permissions().canDelete()).isTrue();
        assertThat(asManager.permissions().canManage()).isTrue();
    }

    @Test
    @DisplayName("详情：未登录不查询我的投票，权限为空")
    void detailWithoutActor() {
        readModel.question = new QuestionRow(7, 1, 100L, "标题", "正文", List.of(), 9L, 0, 1, 0, NOW, NOW);
        readModel.answers = List.of(new AnswerRow(9, 2, "回答", 6, null, NOW, NOW));
        readModel.votes = Map.of("QUESTION:7", 1, "ANSWER:9", 1);

        QuestionDetailView view = queryService.detail(7, null);

        assertThat(view.myVote()).isZero();
        assertThat(view.answers().get(0).myVote()).isZero();
        assertThat(view.answers().get(0).canEdit()).isFalse();
        assertThat(view.permissions()).isEqualTo(PermissionsView.NONE);
    }

    @Test
    @DisplayName("详情：问题不存在时抛出 404")
    void detailMissing() {
        assertThatThrownBy(() -> queryService.detail(99, null))
                .isInstanceOf(NotFoundException.class);
    }

    private static QuestionRow row(long id, long authorId, String title, List<String> tags, Long acceptedAnswerId) {
        return new QuestionRow(id, authorId, 100L, title, title, tags, acceptedAnswerId, 5, 2, 3, NOW, NOW);
    }
}
