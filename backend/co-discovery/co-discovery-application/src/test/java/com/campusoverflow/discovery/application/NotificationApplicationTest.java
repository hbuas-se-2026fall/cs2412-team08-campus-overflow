package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    private final FakeNotifications repo = new FakeNotifications();
    private final FakeIdentity identity = new FakeIdentity();
    private final FakeQa qa = new FakeQa();
    private final NotificationProjector projector = new NotificationProjector(repo, identity, qa);
    private final NotificationService service = new NotificationService(repo);

    @BeforeEach
    void setUp() {
        identity.add(3, "张三");
        identity.add(5, "李四");
    }

    @Test
    void answerSubmittedNotifiesQuestionAuthorOncePerEvent() {
        AnswerSubmittedEvent event = new AnswerSubmittedEvent("evt-1", NOW, 20, 10, 3, 5, "标题");
        projector.on(event);
        projector.on(event);

        assertThat(repo.saveCalls).isEqualTo(1);
        Notification n = repo.byId.get(1L);
        assertThat(n.type()).isEqualTo(NotificationType.ANSWER_RECEIVED);
        assertThat(n.recipientId()).isEqualTo(5);
        assertThat(n.title()).isEqualTo("张三 回答了你的问题");
        assertThat(n.link()).isEqualTo("/questions/10");
        assertThat(n.read()).isFalse();
    }

    @Test
    void selfAnswerDoesNotNotify() {
        projector.on(new AnswerSubmittedEvent("evt-2", NOW, 20, 10, 5, 5, "标题"));

        assertThat(repo.saveCalls).isZero();
    }

    @Test
    void acceptedByOtherNotifiesAnswerAuthor() {
        projector.on(new AnswerAcceptedEvent("evt-3", NOW, 20, 10, 3, 5, 5, "标题"));

        Notification n = repo.byId.get(1L);
        assertThat(n.recipientId()).isEqualTo(3);
        assertThat(n.type()).isEqualTo(NotificationType.ANSWER_ACCEPTED);
        assertThat(n.title()).isEqualTo("你的回答被采纳");
    }

    @Test
    void selfAcceptedDoesNotNotify() {
        projector.on(new AnswerAcceptedEvent("evt-4", NOW, 20, 10, 3, 5, 3, "标题"));

        assertThat(repo.saveCalls).isZero();
    }

    @Test
    void endorsedNotifiesAuthorAndSkipsSelfEndorse() {
        projector.on(new AnswerEndorsedEvent("evt-5", NOW, 20, 10, 3, 5, "标题"));
        projector.on(new AnswerEndorsedEvent("evt-6", NOW, 21, 10, 3, 3, "标题"));

        assertThat(repo.saveCalls).isEqualTo(1);
        assertThat(repo.byId.get(1L).recipientId()).isEqualTo(3);
    }

    @Test
    void mentionNotifiesKnownUserAndIgnoresUnknownName() {
        projector.on(new CommentPostedEvent("evt-7", NOW, 30, 10, 3, "谢谢 @李四 和 @不存在的人"));

        assertThat(repo.saveCalls).isEqualTo(1);
        Notification n = repo.byId.get(1L);
        assertThat(n.recipientId()).isEqualTo(5);
        assertThat(n.type()).isEqualTo(NotificationType.MENTIONED);
        assertThat(n.title()).isEqualTo("张三 在评论中提到了你");
    }

    @Test
    void mentionOfCommentAuthorIsSkipped() {
        projector.on(new CommentPostedEvent("evt-8", NOW, 30, 10, 3, "记录一下 @张三"));

        assertThat(repo.saveCalls).isZero();
    }

    @Test
    void bountyAwardedCarriesQuestionTitle() {
        qa.title = "怎么学并发";
        projector.on(new BountyAwardedEvent("evt-9", NOW, 40, 10, 5, 3, 50));

        Notification n = repo.byId.get(1L);
        assertThat(n.recipientId()).isEqualTo(3);
        assertThat(n.type()).isEqualTo(NotificationType.BOUNTY_AWARDED);
        assertThat(n.title()).isEqualTo("悬赏 50 分已发放：怎么学并发");
    }

    @Test
    void badgeAwardedNotifiesHolder() {
        projector.on(new BadgeAwardedEvent("evt-10", NOW, 3, "FIRST_ANSWER", "首答徽章"));

        Notification n = repo.byId.get(1L);
        assertThat(n.recipientId()).isEqualTo(3);
        assertThat(n.title()).isEqualTo("获得徽章：首答徽章");
    }

    @Test
    void listAndUnreadCountFollowActor() {
        projector.on(new AnswerSubmittedEvent("evt-11", NOW, 20, 10, 3, 5, "标题"));
        Actor recipient = new Actor(5, "李四", Role.STUDENT);

        assertThat(service.list(recipient, new PageRequest(1, 20)).items()).hasSize(1);
        assertThat(service.unreadCount(recipient)).isEqualTo(1);
        assertThat(service.list(new Actor(3, "张三", Role.STUDENT), new PageRequest(1, 20)).items()).isEmpty();
    }

    @Test
    void otherUserCannotMarkRead() {
        projector.on(new AnswerSubmittedEvent("evt-12", NOW, 20, 10, 3, 5, "标题"));

        assertThatThrownBy(() -> service.markRead(new Actor(3, "张三", Role.STUDENT), 1))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markReadMissingNotificationIsNotFound() {
        assertThatThrownBy(() -> service.markRead(new Actor(5, "李四", Role.STUDENT), 99))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllReadReturnsUpdatedCount() {
        projector.on(new AnswerSubmittedEvent("evt-13", NOW, 20, 10, 3, 5, "标题"));
        projector.on(new AnswerAcceptedEvent("evt-14", NOW, 21, 10, 3, 5, 5, "标题"));
        Actor recipient = new Actor(5, "李四", Role.STUDENT);

        assertThat(service.markAllRead(recipient)).isEqualTo(1);
        assertThat(service.unreadCount(recipient)).isZero();
        assertThat(service.markAllRead(recipient)).isZero();
    }

    static final class FakeNotifications implements NotificationRepository {

        final Map<Long, Notification> byId = new LinkedHashMap<>();
        int saveCalls;
        private long seq;

        @Override
        public boolean exists(String sourceEventId, long recipientId) {
            return byId.values().stream()
                    .anyMatch(n -> n.sourceEventId().equals(sourceEventId) && n.recipientId() == recipientId);
        }

        @Override
        public Notification save(Notification notification) {
            saveCalls++;
            if (notification.id() == null) {
                notification.assignId(++seq);
            }
            byId.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public PageResult<Notification> findByRecipient(long recipientId, PageRequest page) {
            List<Notification> items = byId.values().stream()
                    .filter(n -> n.recipientId() == recipientId)
                    .toList();
            return new PageResult<>(items, items.size(), page.page(), page.size());
        }

        @Override
        public long countUnread(long recipientId) {
            return byId.values().stream()
                    .filter(n -> n.recipientId() == recipientId && !n.read())
                    .count();
        }

        @Override
        public int markAllRead(long recipientId) {
            int updated = 0;
            for (Notification n : byId.values()) {
                if (n.recipientId() == recipientId && !n.read()) {
                    n.markRead(recipientId);
                    updated++;
                }
            }
            return updated;
        }
    }

    static final class FakeIdentity implements IdentityApi {

        private final Map<Long, UserSummary> users = new HashMap<>();

        void add(long id, String displayName) {
            users.put(id, new UserSummary(id, displayName, Role.STUDENT, false, null));
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            Map<Long, UserSummary> found = new HashMap<>();
            for (Long id : userIds) {
                UserSummary user = users.get(id);
                if (user != null) {
                    found.put(id, user);
                }
            }
            return found;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return users.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
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

    static final class FakeQa implements QaQueryApi {

        String title;

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return title == null
                    ? Optional.empty()
                    : Optional.of(new QuestionRef(questionId, 5, null, title, null, false));
        }
    }
}
