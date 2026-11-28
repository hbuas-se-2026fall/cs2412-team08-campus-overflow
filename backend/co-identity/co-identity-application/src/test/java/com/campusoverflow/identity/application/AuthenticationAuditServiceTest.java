package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationAuditServiceTest {

    private IdentityTestFixtures.RecordingAudit audit;
    private AuthenticationAuditService service;

    @BeforeEach
    void setUp() {
        audit = IdentityTestFixtures.recordingAudit();
        service = new AuthenticationAuditService(audit);
    }

    @Test
    void loginSucceeded_记录成功并与用户主键关联() {
        service.loginSucceeded(7L, "2024117129");

        assertThat(audit.records()).containsExactly("LOGIN_SUCCESS:7");
    }

    @Test
    void loginFailed_记录失败且不关联用户主键() {
        service.loginFailed("2024117129", "密码错误");

        assertThat(audit.records()).containsExactly("LOGIN_FAILURE:null");
    }
}
