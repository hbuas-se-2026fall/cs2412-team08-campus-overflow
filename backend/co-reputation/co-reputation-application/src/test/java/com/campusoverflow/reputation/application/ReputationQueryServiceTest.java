package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
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
class ReputationQueryServiceTest {

    @Mock private ReputationAccountRepository accounts;
    @Mock private LedgerRepository ledger;
    @Mock private BadgeRepository badges;
    @Mock private IdentityApi identity;
    private ReputationQueryService service;

    private static final Instant NOW = ReputationTestFixtures.NOW;

    @BeforeEach
    void setUp() {
        service = new ReputationQueryService(accounts, ledger, badges, identity);
    }

    private UserSummary summary(long id, String name, Role role, boolean verified) {
        return new UserSummary(id, name, role, verified, "计算机学院");
    }

    @Test
    void profile_账户不存在_抛REP4001() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.profile(1L, new Actor(1, "张三", Role.STUDENT)));

        assertThat(ex.code()).isEqualTo("REP-4001");
    }

    @Test
    void profile_本人查看_返回可用与冻结积分及徽章() {
        ReputationAccount acc = ReputationAccount.reconstitute(1L, 200, 120, 30, 0);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(acc));
        when(identity.findUser(1L)).thenReturn(Optional.of(summary(1L, "张三", Role.TEACHER, true)));
        when(badges.findByUser(1L)).thenReturn(List.of(new BadgeAward(1L, BadgeType.REPUTATION_100, NOW)));

        var view = service.profile(1L, new Actor(1, "张三", Role.TEACHER));

        assertThat(view.displayName()).isEqualTo("张三");
        assertThat(view.reputation()).isEqualTo(200);
        assertThat(view.availablePoints()).isEqualTo(120);
        assertThat(view.frozenPoints()).isEqualTo(30);
        assertThat(view.badges()).hasSize(1);
        assertThat(view.badges().get(0).code()).isEqualTo("REPUTATION_100");
    }

    @Test
    void profile_他人查看_积分置空() {
        ReputationAccount acc = ReputationAccount.reconstitute(1L, 200, 120, 30, 0);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(acc));
        when(identity.findUser(1L)).thenReturn(Optional.of(summary(1L, "张三", Role.TEACHER, true)));
        when(badges.findByUser(1L)).thenReturn(List.of());

        var view = service.profile(1L, new Actor(2, "李四", Role.STUDENT));

        assertThat(view.availablePoints()).isNull();
        assertThat(view.frozenPoints()).isNull();
    }

    @Test
    void profile_身份查不到_昵称为空串() {
        ReputationAccount acc = ReputationAccount.reconstitute(1L, 200, 120, 30, 0);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(acc));
        when(identity.findUser(1L)).thenReturn(Optional.empty());
        when(badges.findByUser(1L)).thenReturn(List.of());

        var view = service.profile(1L, new Actor(2, "李四", Role.STUDENT));

        assertThat(view.displayName()).isEmpty();
    }

    @Test
    void ledger_本人查看_映射流水() {
        LedgerEntry entry = new LedgerEntry(7L, 1L, "evt-1", ReputationReason.ANSWER_ACCEPTED, 20,
                "QUESTION", 10L, NOW);
        PageRequest page = new PageRequest(1, 20);
        when(ledger.findByUser(1L, page)).thenReturn(new PageResult<>(List.of(entry), 1, 1, 20));

        PageResult<LedgerEntryView> result = service.ledger(1L, new Actor(1, "张三", Role.STUDENT), page);

        assertThat(result.items()).hasSize(1);
        LedgerEntryView view = result.items().get(0);
        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.reason()).isEqualTo("ANSWER_ACCEPTED");
        assertThat(view.reasonLabel()).isEqualTo("回答被采纳");
        assertThat(view.delta()).isEqualTo(20);
        assertThat(view.refType()).isEqualTo("QUESTION");
        assertThat(view.refId()).isEqualTo(10L);
    }

    @Test
    void ledger_管理员可查看他人流水() {
        PageRequest page = new PageRequest(1, 20);
        when(ledger.findByUser(1L, page)).thenReturn(PageResult.empty(1, 20));

        PageResult<LedgerEntryView> result = service.ledger(1L, new Actor(9, "管理员", Role.ADMIN), page);

        assertThat(result.total()).isZero();
    }

    @Test
    void ledger_非本人非管理员_抛REP3002() {
        PageRequest page = new PageRequest(1, 20);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.ledger(1L, new Actor(2, "李四", Role.STUDENT), page));

        assertThat(ex.code()).isEqualTo("REP-3002");
    }
}
