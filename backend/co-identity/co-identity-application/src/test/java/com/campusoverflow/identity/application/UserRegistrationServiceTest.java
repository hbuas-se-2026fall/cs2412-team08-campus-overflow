package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.FakeThrottle;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.PrefixHasher;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingAudit;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingEvents;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-001 学生注册 · 应用层用例测试。
 *
 * <p>验证的是<strong>编排</strong>而不是领域规则本身（领域规则由 UserTest 覆盖）：
 * 唯一性校验顺序、集成事件发布、审计脱敏、以及“口令不合格时不落库不广播”。
 */
@DisplayName("US-001 学生注册")
class UserRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryUsers users = new InMemoryUsers();
    private final PrefixHasher hasher = new PrefixHasher();
    private final RecordingEvents events = new RecordingEvents();
    private final RecordingAudit audit = new RecordingAudit();

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(users, hasher, events, audit, CLOCK);
    }

    private RegisterUserCommand command(String username, String displayName, String email) {
        return new RegisterUserCommand(username, displayName, email, "Campus2026", "计算机工程学院", "计科2301");
    }

    @Test
    @DisplayName("SC-001 正常注册：落库为未认证学生，并广播 UserRegisteredEvent 供 Reputation 开户")
    void registersStudentAndPublishesEvent() {
        long id = service.register(command("2023001217", "小明", "Ming@Campus.edu"));

        assertThat(id).isPositive();
        User saved = users.findById(id).orElseThrow();
        assertThat(saved.role()).isEqualTo(Role.STUDENT);
        assertThat(saved.verified()).isFalse();
        assertThat(saved.canLogin()).isTrue();
        // 口令不得以明文落库
        assertThat(saved.passwordHash()).isEqualTo("hashed:Campus2026").isNotEqualTo("Campus2026");

        assertThat(events.ofType(UserRegisteredEvent.class)).singleElement()
                .satisfies(e -> {
                    assertThat(e.userId()).isEqualTo(id);
                    assertThat(e.displayName()).isEqualTo("小明");
                    assertThat(e.role()).isEqualTo(Role.STUDENT);
                    assertThat(e.occurredAt()).isEqualTo(NOW);
                });
    }

    @Test
    @DisplayName("注册后写入审计流水，且学号脱敏（2023001217 → 2023****17）")
    void recordsAuditWithMaskedUsername() {
        long id = service.register(command("2023001217", "小明", "ming@campus.edu"));

        assertThat(audit.byAction("USER_REGISTERED")).singleElement()
                .satisfies(e -> {
                    assertThat(e.actorId()).isEqualTo(id);
                    assertThat(e.detail()).isEqualTo("2023****17");
                });
    }

    @Test
    @DisplayName("邮箱统一小写归一：Ming@Campus.edu 与 ming@campus.edu 视为同一邮箱")
    void normalizesEmailToLowerCase() {
        service.register(command("2023001217", "小明", "Ming@Campus.edu"));

        assertThat(users.findById(1L).orElseThrow().email()).isEqualTo("ming@campus.edu");
    }

    @Test
    @DisplayName("学号重复 → ID-2001（409 Conflict）")
    void rejectsDuplicateUsername() {
        service.register(command("2023001217", "小明", "ming@campus.edu"));

        assertThatThrownBy(() -> service.register(command("2023001217", "小红", "hong@campus.edu")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("学号")
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("ID-2001");
        assertThat(events.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("昵称重复 → ID-2002（昵称用于 @提及，必须全局唯一）")
    void rejectsDuplicateDisplayName() {
        service.register(command("2023001217", "小明", "ming@campus.edu"));

        assertThatThrownBy(() -> service.register(command("2023001218", "小明", "other@campus.edu")))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("ID-2002");
    }

    @Test
    @DisplayName("邮箱重复 → ID-2003（大小写不敏感）")
    void rejectsDuplicateEmailIgnoringCase() {
        service.register(command("2023001217", "小明", "ming@campus.edu"));

        assertThatThrownBy(() -> service.register(command("2023001218", "小红", "MING@CAMPUS.EDU")))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("ID-2003");
    }

    @Test
    @DisplayName("弱口令 → ID-1004，且校验发生在落库与广播之前（不产生脏数据与幽灵事件）")
    void rejectsWeakPasswordBeforePersisting() {
        RegisterUserCommand weak = new RegisterUserCommand("2023001217", "小明", "ming@campus.edu",
                "onlyletters", "计算机工程学院", "计科2301");

        assertThatThrownBy(() -> service.register(weak))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo("ID-1004");
        assertThat(users.count()).isZero();
        assertThat(events.size()).isZero();
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    @DisplayName("学号非法 → ID-1001（4–20 位字母或数字）")
    void rejectsIllegalUsername() {
        assertThatThrownBy(() -> service.register(command("ab", "小明", "ming@campus.edu")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo("ID-1001");
    }

    @Test
    @DisplayName("限流器为独立端口，注册链路不触碰它（防暴力破解只作用于登录）")
    void registrationDoesNotTouchLoginThrottle() {
        FakeThrottle throttle = new FakeThrottle();

        service.register(command("2023001217", "小明", "ming@campus.edu"));

        assertThat(throttle.resets()).isEmpty();
        assertThat(throttle.isBlocked("2023001217")).isFalse();
    }
}
