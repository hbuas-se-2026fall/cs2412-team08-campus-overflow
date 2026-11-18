package com.campusoverflow.discovery.domain.search;

import java.util.Comparator;
import java.util.List;

/**
 * 相关问题推荐（规则引擎，非 AI）：按标签集合的 Jaccard 相似度排序，
 * J(A,B) = |A∩B| / |A∪B|；相似度相同则得分高、已采纳者优先。
 */
public final class RelatedQuestionPolicy {

    private RelatedQuestionPolicy() {
    }

    public static double jaccard(int shared, int sizeA, int sizeB) {
        if (shared <= 0 || sizeA <= 0 || sizeB <= 0) {
            return 0.0;
        }
        int union = sizeA + sizeB - shared;
        if (union <= 0 || shared > union) {
            return 0.0;
        }
        return (double) shared / union;
    }

    /**
     * 相似度降序；相同则得分高优先，再则已采纳优先，再则回答多优先，最后按问题编号保证结果稳定。
     * 没有任何共同标签的候选不进入推荐列表。
     */
    public static List<SearchIndex.RelatedCandidate> rank(List<SearchIndex.RelatedCandidate> candidates, int ownTagCount,
                                                          int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        Comparator<SearchIndex.RelatedCandidate> byRelevance = (left, right) -> {
            int compare = Double.compare(
                    jaccard(right.sharedTags(), ownTagCount, right.tagCount()),
                    jaccard(left.sharedTags(), ownTagCount, left.tagCount()));
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(right.score(), left.score());
            if (compare != 0) {
                return compare;
            }
            if (left.accepted() != right.accepted()) {
                return left.accepted() ? -1 : 1;
            }
            compare = Integer.compare(right.answerCount(), left.answerCount());
            if (compare != 0) {
                return compare;
            }
            return Long.compare(left.questionId(), right.questionId());
        };
        return candidates.stream()
                .filter(candidate -> jaccard(candidate.sharedTags(), ownTagCount, candidate.tagCount()) > 0)
                .sorted(byRelevance)
                .limit(limit)
                .toList();
    }
}
