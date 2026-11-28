package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.api.ReputationSummary;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReputationApiImplTest {

    @Mock private ReputationAccountRepository accounts;
    private ReputationApiImpl api;

    @BeforeEach
    void setUp() {
        api = new ReputationApiImpl(accounts);
    }

    @Test
    void summary_已有账户返回声誉值() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(
                ReputationTestFixtures.account(1L, 88, 30, 0)));

        ReputationSummary summary = api.summary(1L);

        assertThat(summary.userId()).isEqualTo(1L);
        assertThat(summary.reputation()).isEqualTo(88);
    }

    @Test
    void summary_无账户按零声誉返回() {
        when(accounts.findByUserId(5L)).thenReturn(Optional.empty());

        ReputationSummary summary = api.summary(5L);

        assertThat(summary.userId()).isEqualTo(5L);
        assertThat(summary.reputation()).isZero();
    }

    @Test
    void reputations_批量返回用户声誉映射() {
        when(accounts.findAllByUserId(List.of(1L, 2L))).thenReturn(List.of(
                ReputationTestFixtures.account(1L, 120, 0, 0),
                ReputationTestFixtures.account(2L, 40, 0, 0)));

        Map<Long, Integer> result = api.reputations(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 120).containsEntry(2L, 40).hasSize(2);
    }
}
