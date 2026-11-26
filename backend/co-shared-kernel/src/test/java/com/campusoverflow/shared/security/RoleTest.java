package com.campusoverflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void labelsAreChinese() {
        assertThat(Role.STUDENT.label()).isEqualTo("学生");
        assertThat(Role.TA.label()).isEqualTo("助教");
        assertThat(Role.TEACHER.label()).isEqualTo("教师");
        assertThat(Role.ADMIN.label()).isEqualTo("管理员");
    }

    @Test
    void valueOfResolvesAllConstants() {
        for (Role role : Role.values()) {
            assertThat(Role.valueOf(role.name())).isEqualTo(role);
        }
    }
}
