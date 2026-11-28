package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.domain.PasswordHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordHasher hasher;
    @Mock private IntegrationEventPublisher events;
    @Mock private AuditTrail audit;
    @Mock private UserSessionTerminator sessionTerminator;
    @Mock private ObjectProvider<UserSessionTerminator> sessionTerminatorProvider;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, hasher, events, audit, Clock.systemUTC(),
                sessionTerminatorProvider);
    }

    private User makeUser(long id, String username, Role role) {
        User u = User.register(username, "测试", username + "@school.edu.cn", "hash",
                "学院", "班级", Instant.now());
        u.assignId(id);
        return u;
    }

    @Test
    void disable_管理员禁用_成功并清会话() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(sessionTerminatorProvider.getIfAvailable()).thenReturn(sessionTerminator);

        assertDoesNotThrow(() -> service.disable(admin, 2L));
        verify(users).save(target);
        verify(sessionTerminator).terminateAll("zhangsan");
    }

    @Test
    void disable_非管理员_抛403() {
        Actor student = new Actor(99L, "lisi", Role.STUDENT);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));

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
        // User.disable 内部抛 ID-1007 BusinessRuleException
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.disable(admin, 1L));
        assertEquals("ID-1007", ex.code());
    }

    @Test
    void disable_无会话终结器时仅记审计不抛错() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(sessionTerminatorProvider.getIfAvailable()).thenReturn(null);

        service.disable(admin, 2L);

        verify(users).save(target);
        verify(sessionTerminator, never()).terminateAll(anyString());
        verify(audit).record(eq("USER_DISABLED"), eq(1L), eq("USER"), eq("2"), anyString());
    }

    @Test
    void listByVerification_管理员分页查询并脱敏() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findByVerified(false, new PageRequest(0, 10)))
                .thenReturn(new PageResult<>(List.of(target), 1, 0, 10));

        PageResult<UserView> page = service.listByVerification(admin, false, new PageRequest(0, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(2L);
            assertThat(v.displayName()).isEqualTo("测试");
            assertThat(v.verified()).isFalse();
        });
    }

    @Test
    void listByVerification_非管理员抛ID3001() {
        Actor student = new Actor(2L, "小明", Role.STUDENT);

        assertThatThrownBy(() -> service.listByVerification(student, true, new PageRequest(0, 10)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员");
    }

    @Test
    void verify_管理员认证用户并记审计() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));

        service.verify(admin, 2L);

        assertThat(target.verified()).isTrue();
        verify(users).save(target);
        verify(audit).record(eq("USER_VERIFIED"), eq(1L), eq("USER"), eq("2"), isNull());
    }

    @Test
    void verify_重复认证抛ID2006() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        target.verify(admin);
        when(users.findById(2L)).thenReturn(Optional.of(target));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.verify(admin, 2L));
        assertEquals("ID-2006", ex.code());
        verify(users, never()).save(any());
    }

    @Test
    void createStaff_管理员创建教师_发事件并记审计() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        when(hasher.hash("Passw0rd!")).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.assignId(9L);
            return saved;
        });

        long id = service.createStaff(admin,
                new CreateStaffCommand("wanglaoshi", "王老师", "t@school.edu.cn", "Passw0rd!", Role.TEACHER, "计算机学院"));

        assertThat(id).isEqualTo(9L);
        verify(users).save(any(User.class));
        verify(events).publish(any());
        verify(audit).record(eq("STAFF_CREATED"), eq(1L), eq("USER"), eq("9"), anyString());
    }

    @Test
    void createStaff_工号已被占用抛ID2001() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        when(hasher.hash(anyString())).thenReturn("h");
        when(users.existsByUsername("wanglaoshi")).thenReturn(true);

        assertThatThrownBy(() -> service.createStaff(admin,
                new CreateStaffCommand("wanglaoshi", "王老师", "t@school.edu.cn", "Passw0rd!", Role.TEACHER, "计算机学院")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已被使用");
        verify(users, never()).save(any());
    }

    @Test
    void createStaff_学生角色被拒ID1005() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        when(hasher.hash(anyString())).thenReturn("h");

        assertThatThrownBy(() -> service.createStaff(admin,
                new CreateStaffCommand("stu0001", "小明明", "s@school.edu.cn", "Passw0rd!", Role.STUDENT, "计算机学院")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("注册流程");
    }

    @Test
    void createStaff_非管理员抛ID3001() {
        Actor teacher = new Actor(9L, "t0001", Role.TEACHER);

        assertThatThrownBy(() -> service.createStaff(teacher,
                new CreateStaffCommand("wanglaoshi", "王老师", "t@school.edu.cn", "Passw0rd!", Role.TEACHER, "计算机学院")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void changeRole_管理员调整成功_改自己抛ID1006() {
        Actor admin = new Actor(1L, "admin", Role.ADMIN);
        User target = makeUser(2L, "zhangsan", Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(target));

        service.changeRole(admin, 2L, Role.TA);

        assertThat(target.role()).isEqualTo(Role.TA);
        verify(audit).record(eq("ROLE_CHANGED"), eq(1L), eq("USER"), eq("2"), eq("STUDENT -> TA"));

        User self = makeUser(1L, "admin", Role.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(self));
        assertThatThrownBy(() -> service.changeRole(admin, 1L, Role.TEACHER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能修改自己的角色");
    }
}
