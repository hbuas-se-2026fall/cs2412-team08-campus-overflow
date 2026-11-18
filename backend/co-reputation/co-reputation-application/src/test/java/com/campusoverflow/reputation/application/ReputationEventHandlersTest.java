package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.domain.AcademicTerm;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReputationEventHandlersTest {

    @Mock private ReputationAccountRepository accounts;
    @Mock private ReputationLedgerService ledgerService;
    @Mock private LedgerRepository ledger;
    @Mock private BadgeService badges;
    @Mock private BountyService bounties;

    private ReputationEventHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new ReputationEventHandlers(accounts, ledgerService, ledger, badges, bounties,
                ReputationTestFixtures.clock());
    }

    @Test
    void vote_未超每日上限_按正向差值记账() {
        when(ledger.sumPositiveVoteDeltaSince(anyLong(), any())).thenReturn(0);
        VoteCastEvent e = new VoteCastEvent("evt-v1", ReputationTestFixtures.NOW, "ANSWER",
                100L, 1L, 9L, 10L, 0, 1);

        handlers.on(e);

        verify(ledgerService).record(1L, "evt-v1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 100L);
    }

    @Test
    void vote_已达每日上限_超出部分不计() {
        when(ledger.sumPositiveVoteDeltaSince(anyLong(), any())).thenReturn(195);
        VoteCastEvent e = new VoteCastEvent("evt-v2", ReputationTestFixtures.NOW, "ANSWER",
                100L, 1L, 9L, 10L, 0, 1);

        handlers.on(e);

        verify(ledgerService).record(1L, "evt-v2", ReputationReason.ANSWER_VOTE, 5, "ANSWER", 100L);
    }

    @Test
    void vote_负向差值_不受每日上限约束() {
        VoteCastEvent e = new VoteCastEvent("evt-v3", ReputationTestFixtures.NOW, "ANSWER",
                100L, 1L, 9L, 10L, 1, 0);

        handlers.on(e);

        verify(ledgerService).record(1L, "evt-v3", ReputationReason.ANSWER_VOTE, -10, "ANSWER", 100L);
        verify(ledger, never()).sumPositiveVoteDeltaSince(anyLong(), any());
    }

    @Test
    void vote_日起点按校园时区计算() {
        when(ledger.sumPositiveVoteDeltaSince(eq(1L), any())).thenReturn(0);
        VoteCastEvent e = new VoteCastEvent("evt-v4", ReputationTestFixtures.NOW, "ANSWER",
                100L, 1L, 9L, 10L, 0, 1);

        handlers.on(e);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(ledger).sumPositiveVoteDeltaSince(eq(1L), captor.capture());
        Instant expected = LocalDate.of(2026, 10, 8)
                .atStartOfDay(AcademicTerm.CAMPUS_ZONE).toInstant();
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    void accept_采纳后触发悬赏结算() {
        AnswerAcceptedEvent e = new AnswerAcceptedEvent("evt-a1", ReputationTestFixtures.NOW,
                50L, 10L, 3L, 1L, 1L, "测试问题");

        handlers.on(e);

        verify(bounties).settleOnAccept(10L, 50L, 3L, "evt-a1");
    }

    @Test
    void register_账户已存在_跳过开户() {
        ReputationAccount existing = ReputationTestFixtures.account(1L, 0, 100, 0);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(existing));
        UserRegisteredEvent e = new UserRegisteredEvent("evt-r1", ReputationTestFixtures.NOW,
                1L, "学生", Role.STUDENT);

        handlers.on(e);

        verify(accounts, never()).save(any(ReputationAccount.class));
    }
}
