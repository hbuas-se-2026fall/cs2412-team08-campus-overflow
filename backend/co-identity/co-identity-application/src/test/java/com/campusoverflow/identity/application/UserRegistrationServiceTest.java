package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserRegistrationServiceTest {

    private IdentityTestFixtures.InMemoryUsers users;
    private IdentityTestFixtures.RecordingEvents events;
    private IdentityTestFixtures.RecordingAudit audit;
    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        users = IdentityTestFixtures.inMemoryUsers();
        events = IdentityTestFixtures.recordingEvents();
        audit = IdentityTestFixtures.recordingAudit();
        service = new UserRegistrationService(users, IdentityTestFixtures.stubHasher(),
                events, audit, IdentityTestFixtures.CLOCK);
    }

    private RegisterUserCommand command(String username, String displayName, String email, String password) {
        return new RegisterUserCommand(username, displayName, email, password, "计算机学院", "计科2301");
    }

    @Test
    void registeringUserReturnsIdAndPersists() {
        long id = service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThat(id).isPositive();
        assertThat(users.findById(id)).isPresent();
        assertThat(users.findByUsername("2023001217")).isPresent();
    }

    @Test
    void registeringUserPublishesUserRegisteredEvent() {
        long id = service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThat(events.count()).isEqualTo(1);
        UserRegisteredEvent evt = (UserRegisteredEvent) events.events().get(0);
        assertThat(evt.userId()).isEqualTo(id);
        assertThat(evt.displayName()).isEqualTo("小明");
    }

    @Test
    void registeringUserRecordsAudit() {
        service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThat(audit.count()).isEqualTo(1);
        assertThat(audit.records().get(0)).startsWith("USER_REGISTERED");
    }

    @Test
    void rejectsDuplicateUsername() {
        service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThatThrownBy(() -> service.register(command("2023001217", "小红", "Hong@Campus.edu", "abc12345")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该学号/工号已注册");
    }

    @Test
    void rejectsDuplicateDisplayName() {
        service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThatThrownBy(() -> service.register(command("2023001218", "小明", "Hong@Campus.edu", "abc12345")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该昵称已被使用");
    }

    @Test
    void rejectsDuplicateEmail() {
        service.register(command("2023001217", "小明", "Ming@Campus.edu", "abc12345"));
        assertThatThrownBy(() -> service.register(command("2023001218", "小红", "Ming@Campus.edu", "abc12345")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该邮箱已被使用");
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> service.register(command("2023001217", "小明", "Ming@Campus.edu", "short")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("密码");
    }
}
