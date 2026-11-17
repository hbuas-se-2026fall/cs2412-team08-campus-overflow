package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private ReputationAccountRepository accounts;
    @Mock private LedgerRepository ledger;
    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(accounts, ledger);
    }

    @Test
    void reconcile_余额与流水一致_无mismatch() {
        when(accounts.findAll()).thenReturn(List.of(ReputationTestFixtures.account(1L, 15, 0, 0)));
        when(ledger.sumByUser()).thenReturn(Map.of(1L, 15L));

        assertThat(service.reconcile()).isEmpty();
    }

    @Test
    void reconcile_余额与流水不一致_输出明细() {
        when(accounts.findAll()).thenReturn(List.of(ReputationTestFixtures.account(1L, 20, 0, 0)));
        when(ledger.sumByUser()).thenReturn(Map.of(1L, 15L));

        List<ReconciliationService.Mismatch> result = service.reconcile();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).accountReputation()).isEqualTo(20);
        assertThat(result.get(0).ledgerSum()).isEqualTo(15L);
    }
}
