package com.campusoverflow.identity.application;

/**
 * 端口：强制下线某用户的所有服务端会话。
 * 管理员禁用账号时调用，确保被封用户立即失去访问权（不等 Cookie 过期）。
 */
public interface UserSessionTerminator {

    /** 终止 username 的全部会话（Redis/JDBC Session 实现）。 */
    void terminateAll(String username);
}
