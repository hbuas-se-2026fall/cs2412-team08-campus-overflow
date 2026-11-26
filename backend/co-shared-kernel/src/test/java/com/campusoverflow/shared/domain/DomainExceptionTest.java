package com.campusoverflow.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void businessRuleExceptionMapsTo422() {
        BusinessRuleException e = new BusinessRuleException("QA-4001", "标题过短");
        assertThat(e.code()).isEqualTo("QA-4001");
        assertThat(e.getMessage()).isEqualTo("标题过短");
        assertThat(e.suggestedStatus()).isEqualTo(422);
    }

    @Test
    void conflictExceptionMapsTo409() {
        ConflictException e = new ConflictException("ID-3001", "用户名已占用");
        assertThat(e.code()).isEqualTo("ID-3001");
        assertThat(e.suggestedStatus()).isEqualTo(409);
    }

    @Test
    void notFoundExceptionMapsTo404() {
        NotFoundException e = new NotFoundException("QA-4002", "问题不存在");
        assertThat(e.code()).isEqualTo("QA-4002");
        assertThat(e.suggestedStatus()).isEqualTo(404);
    }

    @Test
    void forbiddenExceptionMapsTo403() {
        ForbiddenException e = new ForbiddenException("REP-3002", "仅本人或管理员可见");
        assertThat(e.code()).isEqualTo("REP-3002");
        assertThat(e.suggestedStatus()).isEqualTo(403);
    }

    @Test
    void allSubclassesExtendDomainException() {
        assertThat(new BusinessRuleException("A", "a")).isInstanceOf(DomainException.class);
        assertThat(new ConflictException("B", "b")).isInstanceOf(DomainException.class);
        assertThat(new NotFoundException("C", "c")).isInstanceOf(DomainException.class);
        assertThat(new ForbiddenException("D", "d")).isInstanceOf(DomainException.class);
    }
}
