package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
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

    @BeforeEach
    void setUp() {
        service = new ReputationQueryService(accounts, ledger, badges, identity);
    }

    @Test
    void profile_本人可见积分与他人不可见() {
        when(accounts.findByUserId(1L)).thenReturn(
                Optional.of(ReputationTestFixtures.account(1L, 120, 100, 20)));
        when(identity.findUser(1L)).thenReturn(Optional.of(user(1L, "秦悦", false)));
        when(badges.findByUser(1L)).thenReturn(List.of(
                new BadgeAward(1L, BadgeType.FIRST_ANSWER, ReputationTestFixtures.NOW)));

        ReputationProfileView self = service.profile(1L, new Actor(1L, "秦悦", Role.STUDENT));
        assertThat(self.displayName()).isEqualTo("秦悦");
        assertThat(self.reputation()).isEqualTo(120);
        assertThat(self.availablePoints()).isEqualTo(100);
        assertThat(self.frozenPoints()).isEqualTo(20);
        assertThat(self.badges()).singleElement().satisfies(b -> {
            assertThat(b.code()).isEqualTo("FIRST_ANSWER");
            assertThat(b.name()).isEqualTo("初来乍到");
            assertThat(b.description()).isEqualTo("提交第一个回答");
            assertThat(b.awardedAt()).isEqualTo(ReputationTestFixtures.NOW);
        });

        ReputationProfileView other = service.profile(1L, new Actor(2L, "路人", Role.STUDENT));
        assertThat(other.reputation()).isEqualTo(120);
        assertThat(other.availablePoints()).isNull();
        assertThat(other.frozenPoints()).isNull();
    }

    @Test
    void profile_未登录查看时也不暴露积分() {
        when(accounts.findByUserId(1L)).thenReturn(
                Optional.of(ReputationTestFixtures.account(1L, 50, 40, 10)));
        when(identity.findUser(1L)).thenReturn(Optional.of(user(1L, "秦悦", false)));
        when(badges.findByUser(1L)).thenReturn(List.of());

        ReputationProfileView view = service.profile(1L, null);

        assertThat(view.availablePoints()).isNull();
        assertThat(view.frozenPoints()).isNull();
        assertThat(view.badges()).isEmpty();
    }

    @Test
    void profile_账户不存在抛REP4001() {
        when(accounts.findByUserId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(9L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("声誉账户不存在");
    }

    @Test
    void ledger_本人可分页查看并映射字段() {
        PageRequest page = new PageRequest(0, 5);
        when(ledger.findByUser(1L, page)).thenReturn(new PageResult<>(
                List.of(new LedgerEntry(7L, 1L, "evt-1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 50L,
                        ReputationTestFixtures.NOW)),
                1, 0, 5));

        PageResult<LedgerEntryView> result = service.ledger(1L, new Actor(1L, "秦悦", Role.STUDENT), page);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(7L);
            assertThat(v.reason()).isEqualTo("ANSWER_ACCEPTED");
            assertThat(v.reasonLabel()).isEqualTo("回答被采纳");
            assertThat(v.delta()).isEqualTo(15);
            assertThat(v.refType()).isEqualTo("ANSWER");
            assertThat(v.refId()).isEqualTo(50L);
            assertThat(v.createdAt()).isEqualTo(ReputationTestFixtures.NOW);
        });
    }

    @Test
    void ledger_管理员可查看他人流水() {
        PageRequest page = new PageRequest(0, 5);
        when(ledger.findByUser(2L, page)).thenReturn(PageResult.empty(0, 5));

        PageResult<LedgerEntryView> result = service.ledger(2L, new Actor(99L, "管理员", Role.ADMIN), page);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void ledger_他人查看被拒REP3002() {
        PageRequest page = new PageRequest(0, 5);

        assertThatThrownBy(() -> service.ledger(2L, new Actor(1L, "秦悦", Role.STUDENT), page))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("只能查看自己的声誉流水");
    }

    private static UserSummary user(long id, String name, boolean verified) {
        return new UserSummary(id, name, Role.STUDENT, verified, "计算机学院");
    }
}
