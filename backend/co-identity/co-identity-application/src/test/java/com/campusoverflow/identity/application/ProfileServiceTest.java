package com.campusoverflow.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
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

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserRepository users;
    @Mock private AuditTrail audit;
    @Mock private UserQueryService userQuery;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(users, audit, userQuery);
    }

    private User makeUser(long id, String username, String displayName, String email) {
        User u = User.register(username, displayName, email, "hash", "计算机学院", "计科2412",
                Clock.systemUTC().instant());
        u.assignId(id);
        return u;
    }

    private MeView fakeMeView(long id) {
        return new MeView(id, "zhangsan", "张三", "zhangsan@school.edu.cn", Role.STUDENT, false,
                "计算机学院", "计科2412", List.of());
    }

    @Test
    void updateProfile_自己改成同样值_不报错() {
        Actor actor = new Actor(1L, "zhangsan", Role.STUDENT);
        User user = makeUser(1L, "zhangsan", "张三", "zhangsan@school.edu.cn");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.existsByDisplayName("张三")).thenReturn(true);
        when(users.existsByEmail("zhangsan@school.edu.cn")).thenReturn(true);
        when(userQuery.me(1L)).thenReturn(fakeMeView(1L));

        MeView view = service.updateProfile(actor,
                new UpdateProfileCommand("张三", "zhangsan@school.edu.cn"));
        assertEquals("张三", view.displayName());
        verify(users).save(user);
    }

    @Test
    void updateProfile_昵称被别人占用_抛409() {
        Actor actor = new Actor(1L, "zhangsan", Role.STUDENT);
        User user = makeUser(1L, "zhangsan", "张三", "zhangsan@school.edu.cn");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.existsByDisplayName("李四")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.updateProfile(actor, new UpdateProfileCommand("李四", "zhangsan@school.edu.cn")));
        assertEquals("ID-2002", ex.code());
        verify(users, never()).save(any());
    }

    @Test
    void updateProfile_邮箱被别人占用_抛409() {
        Actor actor = new Actor(1L, "zhangsan", Role.STUDENT);
        User user = makeUser(1L, "zhangsan", "张三", "zhangsan@school.edu.cn");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.existsByEmail("other@school.edu.cn")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.updateProfile(actor, new UpdateProfileCommand("张三", "other@school.edu.cn")));
        assertEquals("ID-2003", ex.code());
    }

    @Test
    void updateProfile_用户不存在_抛404() {
        Actor actor = new Actor(99L, "nobody", Role.STUDENT);
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.updateProfile(actor, new UpdateProfileCommand("新名", "new@school.edu.cn")));
    }
}
