package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void grant_首次授予_落库并发徽章事件() {
        when(badges.has(2L, BadgeType.HELPER_10)).thenReturn(false);

        service.grant(2L, BadgeType.HELPER_10);

        ArgumentCaptor<BadgeAward> award = ArgumentCaptor.forClass(BadgeAward.class);
        verify(badges).award(award.capture());
        assertThat(award.getValue().userId()).isEqualTo(2L);
        assertThat(award.getValue().type()).isEqualTo(BadgeType.HELPER_10);
        assertThat(award.getValue().awardedAt()).isEqualTo(ReputationTestFixtures.NOW);

        ArgumentCaptor<IntegrationEvent> published = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(events).publish(published.capture());
        BadgeAwardedEvent event = (BadgeAwardedEvent) published.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isEqualTo(ReputationTestFixtures.NOW);
        assertThat(event.userId()).isEqualTo(2L);
        assertThat(event.badgeCode()).isEqualTo("HELPER_10");
        assertThat(event.badgeName()).isEqualTo("解惑达人");
    }

    @Test
    void grant_已拥有徽章_幂等不重复发放() {
        when(badges.has(2L, BadgeType.HELPER_10)).thenReturn(true);

        service.grant(2L, BadgeType.HELPER_10);

        verify(badges, never()).award(org.mockito.ArgumentMatchers.any());
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
