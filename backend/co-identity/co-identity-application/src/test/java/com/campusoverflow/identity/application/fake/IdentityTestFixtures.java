package com.campusoverflow.identity.application.fake;

import com.campusoverflow.identity.application.LoginThrottle;
import com.campusoverflow.identity.domain.PasswordHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 应用层测试替身（Test Double）：与 co-qa-application 的 QaTestFixtures 同一套写法——
 * 手写内存实现而非 Mockito，目的是让用例读起来像领域语言，而不是 mock 调用序列。
 */
public final class IdentityTestFixtures {

    private IdentityTestFixtures() {
    }

    /** 内存用户仓储：模拟数据库唯一约束与主键分配。 */
    public static final class InMemoryUsers implements UserRepository {

        private final Map<Long, User> table = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong(0);

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(table.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return table.values().stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public Optional<User> findByDisplayName(String displayName) {
            return table.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByDisplayName(String displayName) {
            return findByDisplayName(displayName).isPresent();
        }

        @Override
        public boolean existsByEmail(String email) {
            return table.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(email));
        }

        @Override
        public List<User> findAllById(Collection<Long> ids) {
            return ids.stream().map(table::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public PageResult<User> findByVerified(boolean verified, PageRequest page) {
            List<User> items = table.values().stream().filter(u -> u.verified() == verified).toList();
            return new PageResult<>(items, items.size(), page.page(), page.size());
        }

        @Override
        public User save(User user) {
            long id = user.id() == null ? sequence.incrementAndGet() : user.id();
            User stored = User.reconstitute(id, user.username(), user.displayName(), user.email(),
                    user.passwordHash(), user.role(), user.status(), user.verified(), user.college(),
                    user.className(), user.createdAt(), user.version());
            table.put(id, stored);
            return stored;
        }

        public int count() {
            return table.size();
        }
    }

    /** 录制集成事件，断言“注册会通知 Reputation 开立声誉账户”。 */
    public static final class RecordingEvents implements IntegrationEventPublisher {

        private final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public List<IntegrationEvent> published() {
            return List.copyOf(published);
        }

        @SuppressWarnings("unchecked")
        public <T extends IntegrationEvent> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(e -> (T) e).toList();
        }

        public int size() {
            return published.size();
        }
    }

    /** 录制审计流水，断言敏感操作留痕且账号脱敏。 */
    public static final class RecordingAudit implements AuditTrail {

        public record Entry(String action, Long actorId, String targetType, String targetId, String detail) {
        }

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            entries.add(new Entry(action, actorId, targetType, targetId, detail));
        }

        public List<Entry> entries() {
            return List.copyOf(entries);
        }

        public Optional<Entry> last() {
            return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(entries.size() - 1));
        }

        public List<Entry> byAction(String action) {
            return entries.stream().filter(e -> e.action().equals(action)).toList();
        }
    }

    /** 可预测的口令哈希桩：不做真实 BCrypt，但保留“明文 ≠ 存储值”的语义。 */
    public static final class PrefixHasher implements PasswordHasher {

        @Override
        public String hash(String raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return hash.equals(hash(raw));
        }
    }

    /** 内存登录限流器：可预置封禁名单，并记录 reset/recordFailure 调用。 */
    public static final class FakeThrottle implements LoginThrottle {

        private final Map<String, Integer> failures = new HashMap<>();
        private final List<String> resets = new ArrayList<>();
        private boolean blocked = false;

        public FakeThrottle blocked() {
            this.blocked = true;
            return this;
        }

        @Override
        public boolean isBlocked(String username) {
            return blocked;
        }

        @Override
        public void recordFailure(String username) {
            failures.merge(username, 1, Integer::sum);
        }

        @Override
        public void reset(String username) {
            resets.add(username);
        }

        public int failuresOf(String username) {
            return failures.getOrDefault(username, 0);
        }

        public List<String> resets() {
            return List.copyOf(resets);
        }
    }
}
