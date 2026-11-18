package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;
    /** 评论聚合时问题自身评论的分组键（回答评论按回答 ID 分组）。 */
    private static final long QUESTION_KEY = -1L;

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        Optional<PageResult<QuestionSummaryView>> cached = cache.get(courseId, page);
        if (cached.isPresent()) {
            return cached.get();
        }
        PageResult<QuestionRow> rows = readModel.listQuestions(courseId, page);
        Map<Long, UserSummary> authors = authorsOf(rows.items().stream().map(QuestionRow::authorId).toList());
        PageResult<QuestionSummaryView> result = rows.map(row -> toSummary(row, authors.get(row.authorId())));
        cache.put(courseId, page, result);
        return result;
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow question = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在或已删除"));
        viewCounter.increment(questionId);
        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);
        long viewerId = actorOrNull == null ? 0L : actorOrNull.userId();
        Map<String, Integer> votes = viewerId == 0L
                ? Map.of()
                : readModel.findMyVotes(viewerId, questionId, answerRows.stream().map(AnswerRow::id).toList());

        Map<Long, UserSummary> users = authorsOf(involvedUsers(question, answerRows, commentRows));
        Map<Long, List<CommentView>> groupedComments = splitComments(commentRows, users);
        boolean viewerManages = viewerId != 0L && identity.canManageCourse(viewerId, question.courseId());
        boolean viewerIsAuthor = viewerId != 0L && actorOrNull.is(question.authorId());
        PermissionsView permissions = viewerId == 0L
                ? PermissionsView.NONE
                : new PermissionsView(viewerIsAuthor || viewerManages, viewerIsAuthor || viewerManages,
                        viewerIsAuthor || viewerManages, viewerManages);

        return new QuestionDetailView(question.id(), question.title(), question.body(),
                renderer.toSafeHtml(question.body()), question.tags(), courseOf(question.courseId()),
                AuthorView.of(question.authorId(), users.get(question.authorId())), question.score(),
                question.answerCount(), question.viewCount(), question.acceptedAnswerId(),
                votes.getOrDefault(voteKey("QUESTION", question.id()), 0), question.createdAt(),
                question.updatedAt(), permissions, groupedComments.get(QUESTION_KEY),
                answerViews(question, answerRows, users, votes, viewerId, viewerManages, actorOrNull, groupedComments));
    }

    public List<TagView> popularTags(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return readModel.popularTags(safeLimit);
    }

    private QuestionSummaryView toSummary(QuestionRow row, UserSummary author) {
        return new QuestionSummaryView(row.id(), row.title(), renderer.toPlainText(row.body(), EXCERPT_LENGTH),
                row.tags(), row.courseId(), AuthorView.of(row.authorId(), author), row.score(), row.answerCount(),
                row.viewCount(), row.acceptedAnswerId() != null, row.createdAt());
    }

    private Map<Long, UserSummary> authorsOf(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return identity.findUsers(new HashSet<>(userIds));
    }

    private Set<Long> involvedUsers(QuestionRow question, List<AnswerRow> answers, List<CommentRow> comments) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(question.authorId());
        answers.forEach(answer -> {
            userIds.add(answer.authorId());
            if (answer.endorsedBy() != null) {
                userIds.add(answer.endorsedBy());
            }
        });
        comments.forEach(comment -> userIds.add(comment.authorId()));
        return userIds;
    }

    private CourseRefView courseOf(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return identity.findCourse(courseId)
                .map((CourseSummary course) -> new CourseRefView(course.id(), course.code(), course.name()))
                .orElse(null);
    }

    private Map<Long, List<CommentView>> splitComments(List<CommentRow> rows, Map<Long, UserSummary> users) {
        Map<Long, List<CommentView>> grouped = new HashMap<>();
        List<CommentView> onQuestion = new ArrayList<>();
        for (CommentRow row : rows) {
            CommentView view = new CommentView(row.id(), AuthorView.of(row.authorId(), users.get(row.authorId())),
                    row.parentId(), row.body(), row.createdAt());
            if ("ANSWER".equals(row.targetType())) {
                grouped.computeIfAbsent(row.targetId(), key -> new ArrayList<>()).add(view);
            } else {
                onQuestion.add(view);
            }
        }
        grouped.put(QUESTION_KEY, onQuestion);
        return grouped;
    }

    private List<AnswerView> answerViews(QuestionRow question, List<AnswerRow> rows,
                                         Map<Long, UserSummary> users, Map<String, Integer> votes, long viewerId,
                                         boolean viewerManages, Actor actorOrNull,
                                         Map<Long, List<CommentView>> groupedComments) {
        Long acceptedId = question.acceptedAnswerId();
        List<AnswerView> views = new ArrayList<>(rows.size());
        for (AnswerRow row : rows) {
            boolean accepted = acceptedId != null && acceptedId == row.id();
            AuthorView endorsedBy = row.endorsedBy() == null
                    ? null
                    : AuthorView.of(row.endorsedBy(), users.get(row.endorsedBy()));
            boolean canEdit = viewerId != 0L && (viewerManages || actorOrNull.is(row.authorId()));
            views.add(new AnswerView(row.id(), row.body(), renderer.toSafeHtml(row.body()),
                    AuthorView.of(row.authorId(), users.get(row.authorId())), row.score(), accepted, endorsedBy,
                    votes.getOrDefault(voteKey("ANSWER", row.id()), 0), canEdit, row.createdAt(), row.updatedAt(),
                    groupedComments.getOrDefault(row.id(), List.of())));
        }
        return views;
    }

    private static String voteKey(String targetType, long targetId) {
        return targetType + ":" + targetId;
    }
}
