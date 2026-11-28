package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseServiceTest {

    private IdentityTestFixtures.InMemoryCourses courses;
    private IdentityTestFixtures.InMemoryUsers users;
    private CourseService service;

    @BeforeEach
    void setUp() {
        courses = IdentityTestFixtures.inMemoryCourses();
        users = IdentityTestFixtures.inMemoryUsers();
        service = new CourseService(courses, users, IdentityTestFixtures.CLOCK);
    }

    private Actor teacher() { return new Actor(1L, "王老师", Role.TEACHER); }
    private Actor student() { return new Actor(2L, "小明", Role.STUDENT); }
    private Actor admin() { return new Actor(3L, "超管", Role.ADMIN); }

    private long createCourse() {
        Course saved = courses.save(Course.create(teacher(), "CS101", "软件工程", "2026-2027-1", IdentityTestFixtures.CLOCK.instant()));
        return saved.id();
    }

    @Test
    void joiningCourseAddsStudentMember() {
        long courseId = createCourse();
        service.join(student(), courseId);
        assertThat(courses.findMembers(courseId)).hasSize(1);
        assertThat(courses.findMember(courseId, student().userId())).isPresent();
        assertThat(courses.findMember(courseId, student().userId()).get().role()).isEqualTo(CourseRole.STUDENT);
    }

    @Test
    void joiningMissingCourseThrowsNotFound() {
        assertThatThrownBy(() -> service.join(student(), 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("课程不存在");
    }

    @Test
    void joiningTwiceThrowsConflict() {
        long courseId = createCourse();
        service.join(student(), courseId);
        assertThatThrownBy(() -> service.join(student(), courseId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("你已加入该课程");
    }

    @Test
    void create_建课并把教师加入为教师成员() {
        long id = service.create(teacher(), new CreateCourseCommand("CS101", "软件工程", "2026-2027-1"));

        assertThat(courses.findById(id)).isPresent();
        assertThat(courses.findMember(id, teacher().userId()).orElseThrow().role()).isEqualTo(CourseRole.TEACHER);
    }

    @Test
    void create_同学期同代码抛ID2004() {
        service.create(teacher(), new CreateCourseCommand("CS101", "软件工程", "2026-2027-1"));

        assertThatThrownBy(() -> service.create(teacher(), new CreateCourseCommand("CS101", "软件工程", "2026-2027-1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("同代码");
    }

    @Test
    void assignRole_教师与管理员可调整_非教师被拒_非成员404() {
        long id = service.create(teacher(), new CreateCourseCommand("CS102", "数据结构", "2026-2027-1"));
        service.join(student(), id);

        service.assignRole(teacher(), id, student().userId(), CourseRole.TA);
        assertThat(courses.findMember(id, student().userId()).orElseThrow().role()).isEqualTo(CourseRole.TA);

        service.assignRole(admin(), id, student().userId(), CourseRole.STUDENT);
        assertThat(courses.findMember(id, student().userId()).orElseThrow().role()).isEqualTo(CourseRole.STUDENT);

        assertThatThrownBy(() -> service.assignRole(student(), id, teacher().userId(), CourseRole.STUDENT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("调整成员角色");
        assertThatThrownBy(() -> service.assignRole(teacher(), id, 77L, CourseRole.STUDENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("不是课程成员");
    }

    @Test
    void listAll_未登录不标记我的角色_成员标记角色_教师名占位() {
        long id = service.create(teacher(), new CreateCourseCommand("CS103", "操作系统", "2026-2027-1"));
        service.join(student(), id);

        assertThat(service.listAll(null)).singleElement().satisfies(v -> {
            assertThat(v.myRole()).isNull();
            assertThat(v.teacherName()).isEqualTo("—");
        });
        assertThat(service.listAll(student())).singleElement()
                .satisfies(v -> assertThat(v.myRole()).isEqualTo("STUDENT"));
        assertThat(service.listAll(teacher())).singleElement()
                .satisfies(v -> assertThat(v.myRole()).isEqualTo("TEACHER"));
    }

    @Test
    void myCourses_只返回已加入的课程() {
        long mine = service.create(teacher(), new CreateCourseCommand("CS104", "编译原理", "2026-2027-1"));
        service.create(teacher(), new CreateCourseCommand("CS105", "计算机网络", "2026-2027-1"));
        service.join(student(), mine);

        assertThat(service.myCourses(student().userId())).singleElement()
                .satisfies(v -> assertThat(v.code()).isEqualTo("CS104"));
        assertThat(service.myCourses(teacher().userId())).hasSize(2);
    }

    @Test
    void members_未知用户填占位_课程不存在404() {
        long id = service.create(teacher(), new CreateCourseCommand("CS106", "软件测试", "2026-2027-1"));
        service.join(student(), id);

        assertThat(service.members(id)).hasSize(2);
        assertThat(service.members(id)).extracting(CourseMemberView::displayName).containsOnly("未知用户");
        assertThatThrownBy(() -> service.members(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("课程不存在");
    }
}
