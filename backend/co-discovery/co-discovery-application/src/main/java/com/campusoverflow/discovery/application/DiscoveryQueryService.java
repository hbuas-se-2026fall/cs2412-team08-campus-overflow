package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    /** 相关问题候选池上限：先粗筛再交给领域策略精排。 */
    private static final int CANDIDATE_LIMIT = 50;
    /** 取不到用户资料时的兜底称呼（与通知中心保持一致）。 */
    private static final String UNKNOWN_NAME = "某同学";

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        if (hits.items().isEmpty()) {
            return hits.map(hit -> toView(hit, null));
        }
        // 批量取作者资料，避免逐条查询（N+1）
        List<Long> authorIds = hits.items().stream().map(SearchHit::authorId).distinct().toList();
        Map<Long, UserSummary> authors = identity.findUsers(authorIds);
        return hits.map(hit -> toView(hit, authors.get(hit.authorId())));
    }

    public List<RelatedQuestionView> related(long questionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> tags = index.findTags(questionId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<SearchIndex.RelatedCandidate> candidates =
                index.findCandidatesSharingTags(questionId, tags, CANDIDATE_LIMIT);
        return RelatedQuestionPolicy.rank(candidates, tags.size(), limit).stream()
                .map(c -> new RelatedQuestionView(c.questionId(), c.title(), c.answerCount(), c.accepted(),
                        RelatedQuestionPolicy.jaccard(c.sharedTags(), tags.size(), c.tagCount())))
                .toList();
    }

    private static SearchResultView toView(SearchHit hit, UserSummary author) {
        SearchResultView.Author view = author == null
                ? new SearchResultView.Author(hit.authorId(), UNKNOWN_NAME, null, false)
                : new SearchResultView.Author(author.id(), author.displayName(),
                        author.role() == null ? null : author.role().name(), author.verified());
        return new SearchResultView(hit.questionId(), hit.title(), hit.snippet(), hit.tags(), hit.courseId(), view,
                hit.score(), hit.answerCount(), hit.accepted(), hit.bountyOpen(), hit.createdAt());
    }
}
