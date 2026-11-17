package com.campusoverflow.reputation.application;

import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 声誉应用层测试共享固件：固定时钟、身份、问题引用、账户与开放悬赏。 */
final class ReputationTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    private ReputationTestFixtures() {
    }

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static Actor teacher(long userId) {
        return new Actor(userId, "王老师", Role.TEACHER);
    }

    static QuestionRef question(long id, Long acceptedAnswerId) {
        return new QuestionRef(id, 1L, 100L, "测试问题", acceptedAnswerId, false);
    }

    static ReputationAccount account(long userId, int reputation, int available, int frozen) {
        return ReputationAccount.reconstitute(userId, reputation, available, frozen, 0);
    }

    static Bounty openBounty(long id, long questionId, long sponsorId, int points) {
        Bounty bounty = Bounty.open(questionId, true, teacher(sponsorId), true, points, 7, NOW);
        bounty.assignId(id);
        return bounty;
    }
}
