package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.shared.domain.ConflictException;
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
}
