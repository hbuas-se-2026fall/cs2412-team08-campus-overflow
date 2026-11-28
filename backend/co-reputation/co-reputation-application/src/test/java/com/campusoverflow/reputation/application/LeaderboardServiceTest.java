package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock private LeaderboardStore store;
    @Mock private ReputationAccountRepository accounts;
    @Mock private IdentityApi identity;
    @Mock private ProcessedEventStore processed;
    @Mock private Clock clock;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardService(store, accounts, identity, processed, clock);
    }

    @Test
    void on_首次事件_累加排行() {
        ReputationChangedEvent e = new ReputationChangedEvent("evt-1", ReputationTestFixtures.NOW,
                1L, 10, 10, "ANSWER_ACCEPTED");
        when(processed.markProcessed("evt-1", "reputation.leaderboard")).thenReturn(true);

        service.on(e);

        verify(store).increment(1L, 10, e.occurredAt());
    }

    @Test
    void on_重复事件_不再累加() {
        ReputationChangedEvent e = new ReputationChangedEvent("evt-1", ReputationTestFixtures.NOW,
                1L, 10, 10, "ANSWER_ACCEPTED");
        when(processed.markProcessed(anyString(), anyString())).thenReturn(false);

        service.on(e);

        verify(store, never()).increment(anyLong(), anyInt(), any());
    }

    @Test
    void top_总榜_按名次补昵称与认证状态() {
        when(accounts.topByReputation(3)).thenReturn(List.of(
                ReputationTestFixtures.account(1L, 200, 100, 0),
                ReputationTestFixtures.account(2L, 150, 80, 0)));
        when(identity.findUsers(anyCollection())).thenReturn(Map.of(
                1L, new UserSummary(1L, "张三", Role.TEACHER, true, "计算机学院"),
                2L, new UserSummary(2L, "李四", Role.STUDENT, false, "计算机学院")));

        List<LeaderboardEntryView> list = service.top(LeaderboardPeriod.ALL, 3);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).rank()).isEqualTo(1);
        assertThat(list.get(0).userId()).isEqualTo(1L);
        assertThat(list.get(0).displayName()).isEqualTo("张三");
        assertThat(list.get(0).verified()).isTrue();
        assertThat(list.get(0).score()).isEqualTo(200);
        assertThat(list.get(1).rank()).isEqualTo(2);
        assertThat(list.get(1).displayName()).isEqualTo("李四");
        assertThat(list.get(1).verified()).isFalse();
    }

    @Test
    void top_总榜_身份缺失_昵称空且未认证() {
        when(accounts.topByReputation(3)).thenReturn(List.of(
                ReputationTestFixtures.account(1L, 200, 100, 0)));
        when(identity.findUsers(anyCollection())).thenReturn(Map.of());

        List<LeaderboardEntryView> list = service.top(LeaderboardPeriod.ALL, 3);

        assertThat(list.get(0).displayName()).isEmpty();
        assertThat(list.get(0).verified()).isFalse();
    }

    @Test
    void top_周期榜_走存储并按名次补昵称() {
        when(clock.instant()).thenReturn(ReputationTestFixtures.NOW);
        when(store.top(LeaderboardPeriod.WEEK, ReputationTestFixtures.NOW, 3)).thenReturn(List.of(
                new LeaderboardStore.Entry(1L, 120),
                new LeaderboardStore.Entry(3L, 80)));
        when(identity.findUsers(anyCollection())).thenReturn(Map.of(
                1L, new UserSummary(1L, "张三", Role.TEACHER, true, "计算机学院")));

        List<LeaderboardEntryView> list = service.top(LeaderboardPeriod.WEEK, 3);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).rank()).isEqualTo(1);
        assertThat(list.get(0).userId()).isEqualTo(1L);
        assertThat(list.get(0).displayName()).isEqualTo("张三");
        assertThat(list.get(0).score()).isEqualTo(120);
        assertThat(list.get(1).userId()).isEqualTo(3L);
        assertThat(list.get(1).displayName()).isEmpty();
    }
}
