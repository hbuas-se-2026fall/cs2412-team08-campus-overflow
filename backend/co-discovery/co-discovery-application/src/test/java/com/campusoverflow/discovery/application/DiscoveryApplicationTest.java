package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内容发现应用层自测：事件投影幂等、搜索视图映射、相关问题排序。
 * 依赖全部用手写 fake，不接数据库与框架。
 */
class DiscoveryApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    // ---------- 搜索索引投影 ----------

    @Test
    void questionPostedIsProjectedAndDuplicateDeliveryIsIgnored() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(posted("evt-1", 10L, 3L, List.of("java", "spring")));
        projector.on(posted("evt-1", 10L, 3L, List.of("java", "spring")));

        assertThat(index.inserted).hasSize(1);
        SearchDocument doc = index.inserted.get(0);
        assertThat(doc.questionId()).isEqualTo(10L);
        assertThat(doc.authorId()).isEqualTo(3L);
        assertThat(doc.courseId()).isNull();
        assertThat(doc.title()).isEqualTo("标题");
        assertThat(doc.tags()).containsExactly("java", "spring");
    }

    @Test
    void questionEditedUpdatesText() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(new QuestionEditedEvent("evt-2", NOW, 10L, "新标题", "新正文", List.of("java")));

        assertThat(index.updated).hasSize(1);
        assertThat(index.updated.get(0)).contains("新标题", "新正文");
    }

    @Test
    void answerSubmittedIncrementsAnswerCountOncePerEvent() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(new AnswerSubmittedEvent("evt-3", NOW, 20L, 10L, 4L, 3L, "标题"));
        projector.on(new AnswerSubmittedEvent("evt-3", NOW, 20L, 10L, 4L, 3L, "标题"));

        assertThat(index.answerCountCalls).containsExactly(1);
    }

    @Test
    void voteOnlyAdjustsQuestionScoreByDelta() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(new VoteCastEvent("evt-4", NOW, "QUESTION", 10L, 3L, 5L, 10L, 1, -1));
        projector.on(new VoteCastEvent("evt-5", NOW, "ANSWER", 20L, 4L, 5L, 10L, 1, -1));

        // 改投：newValue - oldValue = -1 - 1 = -2；ANSWER 投票不进入问题得分
        assertThat(index.scoreDeltas).containsExactly(-2);
    }

    @Test
    void contentDeletedOnlyMarksQuestions() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(new ContentDeletedEvent("evt-6", NOW, "QUESTION", 10L, 10L));
        projector.on(new ContentDeletedEvent("evt-7", NOW, "ANSWER", 20L, 10L));

        assertThat(index.deleted).containsExactly(10L);
    }

    @Test
    void bountyLifecycleTogglesOpenFlag() {
        FakeIndex index = new FakeIndex();
        SearchIndexProjector projector = new SearchIndexProjector(index, new FakeStore());

        projector.on(new BountyOpenedEvent("evt-8", NOW, 30L, 10L, 3L, 50, NOW));
        projector.on(new BountyAwardedEvent("evt-9", NOW, 30L, 10L, 3L, 4L, 50));
        projector.on(new BountyClosedEvent("evt-10", NOW, 31L, 11L, 3L, 50));

        assertThat(index.bountyFlags).containsExactly(true, false, false);
    }

    // ---------- 搜索与相关问题 ----------

    @Test
    void searchMapsHitsAndFetchesAuthorsInOneBatch() {
        FakeIndex index = new FakeIndex();
        FakeSearchPort port = new FakeSearchPort(List.of(hit(10L, 3L), hit(11L, 5L)));
        FakeIdentity identity = new FakeIdentity(Map.of(
                3L, new UserSummary(3L, "张三", Role.STUDENT, false, "计科"),
                5L, new UserSummary(5L, "李四", Role.TEACHER, true, "计科")));
        DiscoveryQueryService service = new DiscoveryQueryService(port, index, identity);

        PageResult<SearchResultView> page = service.search(
                new SearchQuery("java", null, null, null, false, false, new PageRequest(1, 20)));

        assertThat(page.items()).hasSize(2);
        assertThat(identity.batchCalls).isEqualTo(1);
        assertThat(page.items().get(0).author().displayName()).isEqualTo("张三");
        assertThat(page.items().get(0).author().role()).isEqualTo("STUDENT");
        assertThat(page.items().get(1).author().verified()).isTrue();
        assertThat(page.items().get(0).excerpt()).isEqualTo("摘要");
    }

    @Test
    void searchFallsBackToAnonymousNameWhenAuthorMissing() {
        FakeIndex index = new FakeIndex();
        FakeSearchPort port = new FakeSearchPort(List.of(hit(10L, 3L)));
        FakeIdentity identity = new FakeIdentity(Map.of());
        DiscoveryQueryService service = new DiscoveryQueryService(port, index, identity);

        PageResult<SearchResultView> page = service.search(
                new SearchQuery("java", null, null, null, false, false, null));

        assertThat(page.items().get(0).author().displayName()).isEqualTo("某同学");
        assertThat(page.items().get(0).author().verified()).isFalse();
    }

    @Test
    void relatedReturnsEmptyWhenQuestionHasNoTags() {
        FakeIndex index = new FakeIndex();
        index.tags = List.of();
        DiscoveryQueryService service = new DiscoveryQueryService(new FakeSearchPort(List.of()), index,
                new FakeIdentity(Map.of()));

        assertThat(service.related(10L, 5)).isEmpty();
        assertThat(index.candidateCalls).isZero();
    }

    @Test
    void relatedIsRankedBySimilarityAndLimited() {
        FakeIndex index = new FakeIndex();
        index.tags = List.of("java", "spring");
        index.candidates = List.of(
                new SearchIndex.RelatedCandidate(11L, "高相似", 2, 2, 5, 1, false),
                new SearchIndex.RelatedCandidate(12L, "低相似", 1, 3, 9, 0, false));
        DiscoveryQueryService service = new DiscoveryQueryService(new FakeSearchPort(List.of()), index,
                new FakeIdentity(Map.of()));

        List<RelatedQuestionView> views = service.related(10L, 1);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(11L);
        assertThat(views.get(0).similarity()).isEqualTo(1.0);
        assertThat(index.candidateLimit).isEqualTo(50);
    }

    // ---------- fixtures ----------

    private static QuestionPostedEvent posted(String eventId, long questionId, long authorId, List<String> tags) {
        return new QuestionPostedEvent(eventId, NOW, questionId, authorId, null, "标题", "正文", tags);
    }

    private static SearchHit hit(long questionId, long authorId) {
        return new SearchHit(questionId, "标题", "摘要", List.of("java"), null, authorId, 3, 1, false, false, NOW);
    }

    static final class FakeStore implements com.campusoverflow.shared.event.ProcessedEventStore {
        private final Set<String> seen = new HashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return seen.add(consumer + ":" + eventId);
        }
    }

    static final class FakeIndex implements SearchIndex {
        final List<SearchDocument> inserted = new ArrayList<>();
        final List<List<String>> updated = new ArrayList<>();
        final List<Integer> answerCountCalls = new ArrayList<>();
        final List<Integer> scoreDeltas = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Boolean> bountyFlags = new ArrayList<>();
        List<String> tags = List.of("java");
        List<RelatedCandidate> candidates = List.of();
        int candidateCalls;
        int candidateLimit;

        @Override
        public void insert(SearchDocument document) {
            inserted.add(document);
        }

        @Override
        public void updateText(long questionId, String title, String bodyText, List<String> tags, Instant at) {
            updated.add(List.of(title, bodyText));
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta, Instant at) {
            answerCountCalls.add(delta);
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            scoreDeltas.add(delta);
        }

        @Override
        public void markAccepted(long questionId, Instant at) {
        }

        @Override
        public void setBountyOpen(long questionId, boolean open) {
            bountyFlags.add(open);
        }

        @Override
        public void markDeleted(long questionId) {
            deleted.add(questionId);
        }

        @Override
        public List<String> findTags(long questionId) {
            return tags;
        }

        @Override
        public List<RelatedCandidate> findCandidatesSharingTags(long questionId, List<String> tags, int limit) {
            candidateCalls++;
            candidateLimit = limit;
            return candidates;
        }
    }

    static final class FakeSearchPort implements SearchPort {
        private final List<SearchHit> hits;

        FakeSearchPort(List<SearchHit> hits) {
            this.hits = hits;
        }

        @Override
        public PageResult<SearchHit> search(SearchQuery query) {
            return new PageResult<>(hits, hits.size(), 1, 20);
        }
    }

    static final class FakeIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users;
        int batchCalls;

        FakeIdentity(Map<Long, UserSummary> users) {
            this.users = new HashMap<>(users);
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            batchCalls++;
            Map<Long, UserSummary> result = new HashMap<>();
            userIds.forEach(id -> {
                if (users.containsKey(id)) {
                    result.put(id, users.get(id));
                }
            });
            return result;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return Optional.empty();
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.empty();
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return false;
        }
    }
}
