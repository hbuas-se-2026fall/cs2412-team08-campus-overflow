package com.campusoverflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void studentIsNeitherAdminNorTeacher() {
        Actor student = new Actor(2023001L, "小明", Role.STUDENT);
        assertThat(student.isAdmin()).isFalse();
        assertThat(student.isTeacherOrAdmin()).isFalse();
    }

    @Test
    void teacherPassesStaffCheckOnly() {
        Actor teacher = new Actor(2001L, "王老师", Role.TEACHER);
        assertThat(teacher.isAdmin()).isFalse();
        assertThat(teacher.isTeacherOrAdmin()).isTrue();
    }

    @Test
    void adminPassesBothChecks() {
        Actor admin = new Actor(1L, "管理员", Role.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isTeacherOrAdmin()).isTrue();
    }

    @Test
    void assistantIsNotStaff() {
        Actor assistant = new Actor(3001L, "助教", Role.TA);
        assertThat(assistant.isTeacherOrAdmin()).isFalse();
    }

    @Test
    void isComparesUserId() {
        Actor actor = new Actor(2023001L, "小明", Role.STUDENT);
        assertThat(actor.is(2023001L)).isTrue();
        assertThat(actor.is(2023002L)).isFalse();
    }

    @Test
    void exposesRecordComponents() {
        Actor actor = new Actor(7L, "小红", Role.STUDENT);
        assertThat(actor.userId()).isEqualTo(7L);
        assertThat(actor.displayName()).isEqualTo("小红");
        assertThat(actor.role()).isEqualTo(Role.STUDENT);
    }
}
