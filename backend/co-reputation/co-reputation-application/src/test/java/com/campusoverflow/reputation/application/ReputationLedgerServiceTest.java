package com.campusoverflow.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReputationLedgerServiceTest {

    @Mock private ReputationAccountRepository accounts;
    @Mock private LedgerRepository ledger;
    @Mock private BadgeService badges;
    @Mock private IntegrationEventPublisher events;
    private ReputationLedgerService service;

    @BeforeEach
    void setUp() {
        service = new ReputationLedgerService(accounts, ledger, badges, events, Clock.systemUTC());
    }

    @Test
    void record_正常记账_余额增加流水追加事件发布() {
        ReputationAccount acc = ReputationAccount.reconstitute(1L, 10, 0, 0, 0);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(acc));
        when(ledger.exists("evt-1", 1L, ReputationReason.ANSWER_ACCEPTED)).thenReturn(false);

        boolean result = service.record(1L, "evt-1", ReputationReason.ANSWER_ACCEPTED,
                15, "ANSWER", 100L);

        assertTrue(result);
        assertEquals(25, acc.reputation());
        verify(accounts).save(acc);
        verify(ledger).append(any(LedgerEntry.class));
        verify(events).publish(any());
    }

    @Test
    void record_重复事件_幂等忽略() {
        when(ledger.exists("evt-1", 1L, ReputationReason.ANSWER_ACCEPTED)).thenReturn(true);

        boolean result = service.record(1L, "evt-1", ReputationReason.ANSWER_ACCEPTED,
                15, "ANSWER", 100L);

        assertFalse(result);
        verify(accounts, never()).save(any());
        verify(ledger, never()).append(any());
    }

    @Test
    void record_delta为0_不记账() {
        boolean result = service.record(1L, "evt-1", ReputationReason.ANSWER_ACCEPTED,
                0, "ANSWER", 100L);

        assertFalse(result);
        verify(accounts, never()).findByUserId(anyLong());
    }

    @Test
    void record_账户不存在_自动开户() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());
        when(ledger.exists("evt-1", 1L, ReputationReason.ANSWER_VOTE)).thenReturn(false);

        boolean result = service.record(1L, "evt-1", ReputationReason.ANSWER_VOTE,
                10, "ANSWER", 100L);

        assertTrue(result);
        verify(accounts).save(any(ReputationAccount.class));
    }
}
