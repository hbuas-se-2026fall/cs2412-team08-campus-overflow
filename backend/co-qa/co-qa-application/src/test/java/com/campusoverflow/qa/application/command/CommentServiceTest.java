package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryComments;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Title;
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

/** 评论用例编排测试：目标解析、事件发布与二级回复归属。 */
class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final InMemoryComments comments = new InMemoryComments();
    private final RecordingEvents events = new RecordingEvents();

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    private CommentService commentService;
    private long questionId;
    private long answerId;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(comments, questions, answers, events, clock);
        Question question = questions.save(Question.post(ming.userId(), 100L, new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出一个反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        questionId = question.id();
        answerId = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量，仅靠变色无法调整。"), NOW)).id();
    }

    @Test
    @DisplayName("在问题下发表评论：保存并发布 CommentPosted 事件")
    void commentOnQuestion() {
        long id = commentService.post(ming,
                new PostCommentCommand(TargetType.QUESTION, questionId, null, "请问第二步为什么要变色？"));

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(id);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
        assertThat(posted.get(0).authorId()).isEqualTo(ming.userId());
    }

    @Test
    @DisplayName("在回答下发表评论：事件携带所属问题")
    void commentOnAnswer() {
        long id = commentService.post(hong,
                new PostCommentCommand(TargetType.ANSWER, answerId, null, "补充一个直观例子。"));

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(id);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("二级回复：父评论归属同一目标，parentId 透传")
    void replyKeepsParentAndTarget() {
        long parentId = commentService.post(ming,
                new PostCommentCommand(TargetType.ANSWER, answerId, null, "父评论：先问一个问题。"));
        long replyId = commentService.post(hong,
                new PostCommentCommand(TargetType.ANSWER, answerId, parentId, "回复：因为要保持黑高一致。"));

        Comment reply = comments.findById(replyId).orElseThrow();
        assertThat(reply.parentId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("问题或回答不存在时返回 404")
    void missingTargetReturns404() {
        assertThatThrownBy(() -> commentService.post(ming,
                new PostCommentCommand(TargetType.QUESTION, 999L, null, "找不到目标的评论内容")))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QA-4001");
        assertThatThrownBy(() -> commentService.post(ming,
                new PostCommentCommand(TargetType.ANSWER, 999L, null, "找不到目标的评论内容")))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("QA-4002");
    }
}
