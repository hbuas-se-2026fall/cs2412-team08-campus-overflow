package com.campusoverflow.reputation.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Clock;
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
}
