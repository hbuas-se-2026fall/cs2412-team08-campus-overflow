package com.campusoverflow.reputation.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock private BadgeRepository badges;
    @Mock private IntegrationEventPublisher events;
    private BadgeService service;

    @BeforeEach
    void setUp() {
        service = new BadgeService(badges, events, ReputationTestFixtures.clock());
    }

    @Test
    void grant_首次授予_写入徽章并发布事件() {
        when(badges.has(1L, BadgeType.FIRST_ACCEPTED)).thenReturn(false);

        service.grant(1L, BadgeType.FIRST_ACCEPTED);

        verify(badges).award(any(BadgeAward.class));
        verify(events).publish(any(BadgeAwardedEvent.class));
    }

    @Test
    void grant_已授予_幂等跳过() {
        when(badges.has(1L, BadgeType.REPUTATION_100)).thenReturn(true);

        service.grant(1L, BadgeType.REPUTATION_100);

        verify(badges, never()).award(any(BadgeAward.class));
        verify(events, never()).publish(any(BadgeAwardedEvent.class));
    }
}
