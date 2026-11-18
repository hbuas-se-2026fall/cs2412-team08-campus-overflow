package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.PasswordHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseRepository;


public final class IdentityTestFixtures {

    public static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static InMemoryUsers inMemoryUsers() { return new InMemoryUsers(); }
    public static StubHasher stubHasher() { return new StubHasher(); }
    public static RecordingEvents recordingEvents() { return new RecordingEvents(); }
    public static RecordingAudit recordingAudit() { return new RecordingAudit(); }

    /** 内存用户仓库：save 时用 assignId 分配主键。 */
    public static final class InMemoryUsers implements UserRepository {
        private final Map<Long, User> byId = new LinkedHashMap<>();
        private final Map<String, User> byUsername = new HashMap<>();
        private final Map<String, User> byDisplayName = new HashMap<>();
        private final Set<String> emails = new HashSet<>();
        private long nextId = 1L;

        @Override public Optional<User> findById(long id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<User> findByUsername(String username) { return Optional.ofNullable(byUsername.get(username)); }
        @Override public Optional<User> findByDisplayName(String d) { return Optional.ofNullable(byDisplayName.get(d)); }
        @Override public boolean existsByUsername(String u) { return byUsername.containsKey(u); }
        @Override public boolean existsByDisplayName(String d) { return byDisplayName.containsKey(d); }
        @Override public boolean existsByEmail(String e) { return emails.contains(e); }
        @Override public List<User> findAllById(Collection<Long> ids) {
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        }
        @Override public PageResult<User> findByVerified(boolean verified, PageRequest page) {
            List<User> all = byId.values().stream().filter(u -> u.verified() == verified).toList();
            return new PageResult<>(all, all.size(), page.page(), page.size());
        }
        @Override public User save(User user) {
            if (user.id() == null) { user.assignId(nextId++); }
            byId.put(user.id(), user);
            byUsername.put(user.username(), user);
            byDisplayName.put(user.displayName(), user);
            emails.add(user.email());
            return user;
        }
    }

    /** 假哈希：前缀法，便于断言。 */
    public static final class StubHasher implements PasswordHasher {
        @Override public String hash(String raw) { return "stub$" + raw; }
        @Override public boolean matches(String raw, String hash) { return hash("stub$" + raw).equals(hash); }
    }

    /** 记录所有发布的事件。 */
    public static final class RecordingEvents implements IntegrationEventPublisher {
        private final List<IntegrationEvent> published = new ArrayList<>();
        @Override public void publish(IntegrationEvent event) { published.add(event); }
        public List<IntegrationEvent> events() { return published; }
        public int count() { return published.size(); }
    }

    /** 记录审计动作。 */
    public static final class RecordingAudit implements AuditTrail {
        private final List<String> records = new ArrayList<>();
        @Override public void record(String action, Long entityId, String entityType, String detail, String subject) {
            records.add(action + ":" + entityId);
        }
        public List<String> records() { return records; }
        public int count() { return records.size(); }
    }

    public static InMemoryCourses inMemoryCourses() { return new InMemoryCourses(); }

    /** 内存课程仓库：save 分配 id，成员按 courseId:userId 存。 */
    public static final class InMemoryCourses implements CourseRepository {
        private final Map<Long, Course> byId = new LinkedHashMap<>();
        private final Set<String> codeTerms = new HashSet<>();
        private final Map<String, CourseMember> membersByKey = new LinkedHashMap<>();
        private long nextId = 1L;

        private static String key(long courseId, long userId) { return courseId + ":" + userId; }

        @Override public Course save(Course course) {
            if (course.id() == null) { course.assignId(nextId++); }
            byId.put(course.id(), course);
            codeTerms.add(course.code() + ":" + course.term());
            return course;
        }
        @Override public Optional<Course> findById(long id) { return Optional.ofNullable(byId.get(id)); }
        @Override public List<Course> findAll() { return new ArrayList<>(byId.values()); }
        @Override public boolean existsByCodeAndTerm(String code, String term) { return codeTerms.contains(code + ":" + term); }
        @Override public Optional<CourseMember> findMember(long courseId, long userId) { return Optional.ofNullable(membersByKey.get(key(courseId, userId))); }
        @Override public CourseMember saveMember(CourseMember member) {
            membersByKey.put(key(member.courseId(), member.userId()), member);
            return member;
        }
        @Override public List<CourseMember> findMembers(long courseId) {
            return membersByKey.values().stream().filter(m -> m.courseId() == courseId).toList();
        }
        @Override public List<CourseMember> findMembershipsOfUser(long userId) {
            return membersByKey.values().stream().filter(m -> m.userId() == userId).toList();
        }
    }

}


