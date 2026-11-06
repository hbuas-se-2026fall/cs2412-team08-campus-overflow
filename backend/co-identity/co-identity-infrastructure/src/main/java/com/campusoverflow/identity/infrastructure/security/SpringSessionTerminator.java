package com.campusoverflow.identity.infrastructure.security;

import com.campusoverflow.identity.application.UserSessionTerminator;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Session 的会话强制下线实现。
 * 注入 {@link FindByIndexNameSessionRepository}（由 spring-session-data-redis 自动配置），
 * 按 principal name（username）查出该用户全部会话并逐个删除。
 */
@Component
public class SpringSessionTerminator implements UserSessionTerminator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SpringSessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void terminateAll(String username) {
        // PRINCIPAL_NAME_INDEX_NAME = "principal_name"，Spring Session 默认按登录用户名建索引
        var sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
    }
}
