package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserQueryServiceTest {

    private IdentityTestFixtures.InMemoryUsers users;
    private IdentityTestFixtures.InMemoryCourses courses;
    private UserQueryService service;

    @BeforeEach
    void setUp() {
        users = IdentityTestFixtures.inMemoryUsers();
        courses = IdentityTestFixtures.inMemoryCourses();
        service = new UserQueryService(users, new CourseService(courses, users, IdentityTestFixtures.CLOCK));
    }

    @Test
    void me_返回本人资料与已加入课程() {
        User u = User.register("2024117129", "秦悦", "2024117129@school.edu.cn", "hash",
                "计算机学院", "计科2412", IdentityTestFixtures.NOW);
        users.save(u);
        Course c = Course.create(new Actor(9L, "王老师", Role.TEACHER), "CS101", "软件工程",
                "2026-2027-1", IdentityTestFixtures.NOW);
        courses.save(c);
        courses.saveMember(CourseMember.student(c.id(), u.id(), IdentityTestFixtures.NOW));

        MeView me = service.me(u.id());

        assertThat(me.id()).isEqualTo(u.id());
        assertThat(me.displayName()).isEqualTo("秦悦");
        assertThat(me.college()).isEqualTo("计算机学院");
        assertThat(me.className()).isEqualTo("计科2412");
        assertThat(me.courses()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo("CS101");
            assertThat(v.myRole()).isEqualTo("STUDENT");
            assertThat(v.teacherName()).isEqualTo("—");
        });
    }

    @Test
    void me_用户不存在抛ID4001() {
        assertThatThrownBy(() -> service.me(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("用户不存在");
    }
}
