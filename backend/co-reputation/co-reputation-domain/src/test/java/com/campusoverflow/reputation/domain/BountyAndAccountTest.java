package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BountyAndAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @Test
    void onlyCourseManagerCanOpenBountyWithinLimits() {
        assertThatThrownBy(() -> Bounty.open(10, true, new Actor(1, "学生", Role.STUDENT), false, 50, 7, NOW))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, false, 50, 7, NOW))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 5, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, false, teacher, true, 50, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
        Bounty b = Bounty.open(10, true, teacher, true, 50, 7, NOW);
        assertThat(b.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(b.status()).isEqualTo(BountyStatus.OPEN);
    }

    @Test
    void bountyCanBeSettledOnlyOnce() {
        Bounty b = Bounty.open(10, true, teacher, true, 50, 7, NOW);
        b.award(50, 3, NOW.plusSeconds(60));
        assertThat(b.status()).isEqualTo(BountyStatus.AWARDED);
        assertThat(b.winnerId()).isEqualTo(3L);
        assertThatThrownBy(() -> b.close(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void sponsorCannotWinOwnBountyAndDueDetectionWorks() {
        Bounty b = Bounty.open(10, true, teacher, true, 50, 1, NOW);
        assertThatThrownBy(() -> b.award(50, 9, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThat(b.isDue(NOW)).isFalse();
        assertThat(b.isDue(NOW.plus(Duration.ofDays(1)))).isTrue();
    }

    @Test
    void freezeSpendAndRefundKeepPointsConsistent() {
        ReputationAccount acc = ReputationAccount.open(9, 1000);
        acc.freezePoints(300);
        assertThat(acc.availablePoints()).isEqualTo(700);
        assertThat(acc.frozenPoints()).isEqualTo(300);
        acc.spendFrozen(100);
        acc.refundFrozen(200);
        assertThat(acc.availablePoints()).isEqualTo(900);
        assertThat(acc.frozenPoints()).isZero();
        assertThatThrownBy(() -> acc.freezePoints(901)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void badgePolicyThresholds() {
        assertThat(BadgePolicy.forAcceptedCount(0)).isEmpty();
        assertThat(BadgePolicy.forAcceptedCount(10)).containsExactlyInAnyOrder(BadgeType.FIRST_ACCEPTED, BadgeType.HELPER_10);
        assertThat(BadgePolicy.forReputation(150)).containsExactly(BadgeType.REPUTATION_100);
    }

    @Test
    void academicTermKeysFollowCampusCalendar() {
        assertThat(AcademicTerm.termKey(Instant.parse("2026-10-08T02:00:00Z"))).isEqualTo("2026-FALL");
        assertThat(AcademicTerm.termKey(Instant.parse("2027-01-10T02:00:00Z"))).isEqualTo("2026-FALL");
        assertThat(AcademicTerm.termKey(Instant.parse("2027-03-01T02:00:00Z"))).isEqualTo("2027-SPRING");
        assertThat(AcademicTerm.monthKey(Instant.parse("2026-10-08T02:00:00Z"))).isEqualTo("2026-10");
        assertThat(AcademicTerm.weekKey(Instant.parse("2026-10-08T02:00:00Z"))).isEqualTo("2026-W41");
    }

    @Test
    void bountyPointsAndDaysRespectLimits() {
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 501, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 9, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 0, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 15, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(Bounty.open(10, true, teacher, true, 10, 1, NOW).points()).isEqualTo(10);
        assertThat(Bounty.open(10, true, teacher, true, 500, 14, NOW).points()).isEqualTo(500);
    }

    @Test
    void closedBountyCannotBeAwardedOrReopened() {
        Bounty expired = Bounty.open(10, true, teacher, true, 50, 1, NOW);
        expired.close(NOW.plus(Duration.ofDays(2)));
        assertThat(expired.status()).isEqualTo(BountyStatus.EXPIRED);
        assertThatThrownBy(() -> expired.close(NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> expired.award(50, 3, NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void accountRejectsNonPositiveAndInsufficientFrozen() {
        ReputationAccount acc = ReputationAccount.open(9, 1000);
        acc.freezePoints(300);
        assertThatThrownBy(() -> acc.spendFrozen(0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> acc.refundFrozen(-1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> acc.spendFrozen(301)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> acc.refundFrozen(301)).isInstanceOf(BusinessRuleException.class);
        acc.applyReputation(50);
        assertThat(acc.reputation()).isEqualTo(50);
    }

    @Test
    void reasonLabelsAndVoteClassification() {
        assertThat(ReputationReason.ANSWER_ACCEPTED.label()).isEqualTo("回答被采纳");
        assertThat(ReputationReason.QUESTION_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.ANSWER_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.BOUNTY_AWARDED.isVote()).isFalse();
    }

    @Test
    void ledgerEntryRejectsNullReasonAndEvent() {
        assertThatThrownBy(() -> LedgerEntry.of(1, null, ReputationReason.ANSWER_ACCEPTED, 10,
                "QUESTION", 10, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LedgerEntry.of(1, "evt-1", null, 10,
                "QUESTION", 10, NOW)).isInstanceOf(NullPointerException.class);
        LedgerEntry entry = LedgerEntry.of(1, "evt-1", ReputationReason.ANSWER_ACCEPTED, 10,
                "QUESTION", 10, NOW);
        assertThat(entry.delta()).isEqualTo(10);
    }
}
