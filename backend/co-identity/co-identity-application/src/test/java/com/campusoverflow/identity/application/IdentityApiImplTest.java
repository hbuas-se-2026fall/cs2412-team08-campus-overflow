package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityApiImplTest {

    private IdentityTestFixtures.InMemoryUsers users;
    private IdentityTestFixtures.InMemoryCourses courses;
    private IdentityApiImpl api;

    @BeforeEach
    void setUp() {
        users = IdentityTestFixtures.inMemoryUsers();
        courses = IdentityTestFixtures.inMemoryCourses();
        api = new IdentityApiImpl(users, courses);
    }

    private User student(String username, String displayName) {
        User u = User.register(username, displayName, username + "@school.edu.cn", "hash",
                "计算机学院", "计科2412", IdentityTestFixtures.NOW);
        users.save(u);
        return u;
    }

    private User staff(Actor admin, String username, String displayName, Role role) {
        User u = User.createStaff(admin, username, displayName, username + "@school.edu.cn", "hash", role,
                "计算机学院", IdentityTestFixtures.NOW);
        users.save(u);
        return u;
    }

    private Course course(Actor teacher, String code, String name) {
        Course c = Course.create(teacher, code, name, "2026-2027-1", IdentityTestFixtures.NOW);
        courses.save(c);
        return c;
    }

    @Test
    void findUser_命中返回摘要_未命中返回空() {
        User u = student("2024117129", "秦悦");

        Optional<UserSummary> found = api.findUser(u.id());

        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo("秦悦");
        assertThat(found.get().role()).isEqualTo(Role.STUDENT);
        assertThat(found.get().verified()).isFalse();
        assertThat(api.findUser(999L)).isEmpty();
    }

    @Test
    void findUsers_空集合或null返回空映射() {
        student("stu001", "小明");

        assertThat(api.findUsers(null)).isEmpty();
        assertThat(api.findUsers(List.of())).isEmpty();
    }

    @Test
    void findUsers_去重后批量映射() {
        User a = student("stu001", "小明");
        User b = student("stu002", "小红");

        Map<Long, UserSummary> map = api.findUsers(List.of(a.id(), b.id(), a.id()));

        assertThat(map).hasSize(2).containsKeys(a.id(), b.id());
        assertThat(map.get(b.id()).displayName()).isEqualTo("小红");
    }

    @Test
    void findByDisplayName_命中与未命中() {
        student("stu003", "张嘉雨");

        assertThat(api.findByDisplayName("张嘉雨")).isPresent();
        assertThat(api.findByDisplayName("查无此人")).isEmpty();
    }

    @Test
    void findCourse_命中返回摘要_未命中返回空() {
        Course c = course(new Actor(9L, "王老师", Role.TEACHER), "CS101", "软件工程");

        Optional<CourseSummary> found = api.findCourse(c.id());

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("CS101");
        assertThat(found.get().name()).isEqualTo("软件工程");
        assertThat(found.get().teacherId()).isEqualTo(9L);
        assertThat(api.findCourse(999L)).isEmpty();
    }

    @Test
    void canManageCourse_未知用户一律false() {
        assertThat(api.canManageCourse(999L, null)).isFalse();
        assertThat(api.canManageCourse(999L, 1L)).isFalse();
    }

    @Test
    void canManageCourse_管理员恒true() {
        Actor adminActor = new Actor(100L, "超管", Role.ADMIN);
        User admin = staff(adminActor, "root001", "超管", Role.ADMIN);
        Course c = course(new Actor(9L, "王老师", Role.TEACHER), "CS102", "数据结构");

        assertThat(api.canManageCourse(admin.id(), null)).isTrue();
        assertThat(api.canManageCourse(admin.id(), c.id())).isTrue();
    }

    @Test
    void canManageCourse_教师无课程时按角色判定_学生false() {
        Actor adminActor = new Actor(100L, "超管", Role.ADMIN);
        User teacher = staff(adminActor, "t0001", "李老师", Role.TEACHER);
        User stu = student("stu004", "秦悦");

        assertThat(api.canManageCourse(teacher.id(), null)).isTrue();
        assertThat(api.canManageCourse(stu.id(), null)).isFalse();
    }

    @Test
    void canManageCourse_按课程成员角色判定() {
        Actor adminActor = new Actor(100L, "超管", Role.ADMIN);
        User teacher = staff(adminActor, "t0002", "王老师", Role.TEACHER);
        User stu = student("stu005", "小明");
        Course c = course(new Actor(teacher.id(), "王老师", Role.TEACHER), "CS103", "操作系统");
        courses.saveMember(CourseMember.teacher(c.id(), teacher.id(), IdentityTestFixtures.NOW));
        courses.saveMember(CourseMember.student(c.id(), stu.id(), IdentityTestFixtures.NOW));

        assertThat(api.canManageCourse(teacher.id(), c.id())).isTrue();
        assertThat(api.canManageCourse(stu.id(), c.id())).isFalse();
        assertThat(api.canManageCourse(stu.id(), 999L)).isFalse();
    }
}
