package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void top_ALL_按账户声誉排名并补齐昵称与认证() {
        when(accounts.topByReputation(2)).thenReturn(List.of(
                ReputationTestFixtures.account(1L, 300, 300, 0),
                ReputationTestFixtures.account(2L, 200, 200, 0)));
        when(identity.findUsers(List.of(1L, 2L))).thenReturn(Map.of(
                1L, new UserSummary(1L, "秦悦", Role.STUDENT, false, "计算机学院"),
                2L, new UserSummary(2L, "王老师", Role.TEACHER, true, "计算机学院")));

        List<LeaderboardEntryView> result = service.top(LeaderboardPeriod.ALL, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).displayName()).isEqualTo("秦悦");
        assertThat(result.get(0).verified()).isFalse();
        assertThat(result.get(0).score()).isEqualTo(300L);
        assertThat(result.get(1).rank()).isEqualTo(2);
        assertThat(result.get(1).displayName()).isEqualTo("王老师");
        assertThat(result.get(1).verified()).isTrue();
        assertThat(result.get(1).score()).isEqualTo(200L);
    }

    @Test
    void top_WEEK_读存储分数_缺失用户昵称为空() {
        when(clock.instant()).thenReturn(ReputationTestFixtures.NOW);
        when(store.top(LeaderboardPeriod.WEEK, ReputationTestFixtures.NOW, 2)).thenReturn(List.of(
                new LeaderboardStore.Entry(3L, 90), new LeaderboardStore.Entry(4L, 70)));
        when(identity.findUsers(List.of(3L, 4L))).thenReturn(Map.of(
                3L, new UserSummary(3L, "张嘉雨", Role.STUDENT, false, "计算机学院")));

        List<LeaderboardEntryView> result = service.top(LeaderboardPeriod.WEEK, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).displayName()).isEqualTo("张嘉雨");
        assertThat(result.get(0).score()).isEqualTo(90L);
        assertThat(result.get(1).rank()).isEqualTo(2);
        assertThat(result.get(1).displayName()).isEmpty();
        assertThat(result.get(1).verified()).isFalse();
        assertThat(result.get(1).score()).isEqualTo(70L);
    }
}
