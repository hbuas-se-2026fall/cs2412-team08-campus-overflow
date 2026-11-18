package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BountyServiceTest {

    @Mock private BountyRepository bounties;
    @Mock private ReputationAccountRepository accounts;
    @Mock private ReputationLedgerService ledgerService;
    @Mock private QaQueryApi qa;
    @Mock private IdentityApi identity;
    @Mock private IntegrationEventPublisher events;
    @Mock private AuditTrail audit;

    private BountyService service;

    @BeforeEach
    void setUp() {
        service = new BountyService(bounties, accounts, ledgerService, qa, identity, events, audit,
                ReputationTestFixtures.clock());
    }

    @Test
    void settle_非发起人中奖_冻结转出并记悬赏账() {
        Bounty bounty = ReputationTestFixtures.openBounty(1L, 10L, 9L, 50);
        ReputationAccount sponsor = ReputationTestFixtures.account(9L, 0, 1000, 50);
        when(bounties.findOpenByQuestion(10L)).thenReturn(Optional.of(bounty));
        when(accounts.findByUserId(9L)).thenReturn(Optional.of(sponsor));

        service.settleOnAccept(10L, 50L, 3L, "evt-1");

        verify(ledgerService).record(3L, "evt-1", ReputationReason.BOUNTY_AWARDED, 50, "BOUNTY", 1L);
        verify(events).publish(any(BountyAwardedEvent.class));
        verify(accounts).save(sponsor);
        verify(audit).record(eq("BOUNTY_AWARDED"), eq(9L), eq("BOUNTY"), eq("1"), any());
        assertThat(bounty.winnerId()).isEqualTo(3L);
    }

    @Test
    void settle_发起人自赢_关闭退款不发奖励() {
        Bounty bounty = ReputationTestFixtures.openBounty(1L, 10L, 9L, 50);
        ReputationAccount sponsor = ReputationTestFixtures.account(9L, 0, 1000, 50);
        when(bounties.findOpenByQuestion(10L)).thenReturn(Optional.of(bounty));
        when(accounts.findByUserId(9L)).thenReturn(Optional.of(sponsor));

        service.settleOnAccept(10L, 50L, 9L, "evt-1");

        verify(ledgerService, never()).record(anyLong(), anyString(), any(), anyInt(), anyString(), anyLong());
        verify(events).publish(any(BountyClosedEvent.class));
        verify(events, never()).publish(any(BountyAwardedEvent.class));
        verify(accounts).save(sponsor);
        verify(audit).record(eq("BOUNTY_CLOSED"), eq(9L), eq("BOUNTY"), eq("1"), any());
        assertThat(bounty.winnerId()).isNull();
    }

    @Test
    void settle_发起人账户缺失_只关闭不发奖励() {
        Bounty bounty = ReputationTestFixtures.openBounty(1L, 10L, 9L, 50);
        when(bounties.findOpenByQuestion(10L)).thenReturn(Optional.of(bounty));
        when(accounts.findByUserId(9L)).thenReturn(Optional.empty());

        service.settleOnAccept(10L, 50L, 3L, "evt-1");

        verify(ledgerService, never()).record(anyLong(), anyString(), any(), anyInt(), anyString(), anyLong());
        verify(events).publish(any(BountyClosedEvent.class));
        verify(events, never()).publish(any(BountyAwardedEvent.class));
        verify(accounts, never()).save(any());
        verify(audit).record(eq("BOUNTY_CLOSED"), eq(9L), eq("BOUNTY"), eq("1"), any());
        assertThat(bounty.winnerId()).isNull();
    }

    @Test
    void open_问题已采纳_拒绝发起悬赏() {
        when(qa.findQuestion(10L)).thenReturn(Optional.of(ReputationTestFixtures.question(10L, 99L)));

        assertThrows(BusinessRuleException.class,
                () -> service.open(ReputationTestFixtures.teacher(9L), 10L, 50, 7));
        verify(bounties, never()).save(any());
    }

    @Test
    void open_已有进行中悬赏_冲突拒绝() {
        when(qa.findQuestion(10L)).thenReturn(Optional.of(ReputationTestFixtures.question(10L, null)));
        when(bounties.findOpenByQuestion(10L))
                .thenReturn(Optional.of(ReputationTestFixtures.openBounty(1L, 10L, 9L, 50)));

        assertThrows(ConflictException.class,
                () -> service.open(ReputationTestFixtures.teacher(9L), 10L, 50, 7));
    }

    @Test
    void expireDue_关闭到期悬赏并退回冻结积分() {
        Bounty bounty = ReputationTestFixtures.openBounty(1L, 10L, 9L, 50);
        ReputationAccount sponsor = ReputationTestFixtures.account(9L, 0, 1000, 50);
        when(bounties.findDue(any(Instant.class), eq(100))).thenReturn(List.of(bounty));
        when(accounts.findByUserId(9L)).thenReturn(Optional.of(sponsor));

        int closed = service.expireDue();

        assertThat(closed).isEqualTo(1);
        verify(events).publish(any(BountyClosedEvent.class));
        verify(accounts).save(sponsor);
        verify(audit).record(eq("BOUNTY_CLOSED"), eq(9L), eq("BOUNTY"), eq("1"), any());
    }

    @Test
    void latest_回填发起人昵称() {
        Bounty bounty = ReputationTestFixtures.openBounty(1L, 10L, 9L, 50);
        when(bounties.findLatestByQuestion(10L)).thenReturn(Optional.of(bounty));
        when(identity.findUser(9L)).thenReturn(Optional.of(
                new UserSummary(9L, "王老师", Role.TEACHER, false, "计科")));

        assertThat(service.latest(10L)).isPresent();
        assertThat(service.latest(10L).orElseThrow().sponsorName()).isEqualTo("王老师");
    }
}
