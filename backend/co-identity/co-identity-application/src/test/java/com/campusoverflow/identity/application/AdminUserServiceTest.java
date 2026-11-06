package com.campusoverflow.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.domain.PasswordHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordHasher hasher;
    @Mock private IntegrationEventPublisher events;
    @Mock private AuditTrail audit;
    @Mock private UserSessionTerminator sessionTerminator;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, hasher, events, audit, Clock.systemUTC(), sessionTerminator);
    }

    private User makeUser(long id, String username, Role role) {
        User u = User.register(username, "测试", username + "@school.edu.cn", "hash",
                "学院", "班级", Instant.now());
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    @Test
    void disable_管理员禁用_成功并清会话() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));

        assertDoesNotThrow(() -> service.disable(admin, 2L));
        verify(users).save(target);
        verify(sessionTerminator).terminateAll("zhangsan");
    }

    @Test
    void disable_非管理员_抛403() {
        Actor student = new Actor(99L, "lisi", Role.STUDENT);
        assertThrows(ForbiddenException.class, () -> service.disable(student, 2L));
        verify(users, never()).save(any());
        verify(sessionTerminator, never()).terminateAll(anyString());
    }

    @Test
    void disable_用户不存在_抛404() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        when(users.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.disable(admin, 999L));
    }

    @Test
    void disable_禁用自己_抛业务异常() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User self = makeUser(1L, "admin", Role.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(self));
        // User.disable 内部会抛 ID-1007 BusinessRuleException
        assertThrows(RuntimeException.class, () -> service.disable(admin, 1L));
    }
}
