package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.domain.ReputationAccount;
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
    void summary_账户存在_返回声誉() {
        when(accounts.findByUserId(1L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(1L, 200, 100, 0, 0)));

        assertThat(api.summary(1L).reputation()).isEqualTo(200);
    }

    @Test
    void summary_账户缺失_声誉记为零() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(api.summary(1L).reputation()).isZero();
    }

    @Test
    void reputations_批量映射用户声誉() {
        when(accounts.findAllByUserId(List.of(1L, 2L))).thenReturn(List.of(
                ReputationAccount.reconstitute(1L, 200, 100, 0, 0),
                ReputationAccount.reconstitute(2L, 150, 80, 0, 0)));

        Map<Long, Integer> result = api.reputations(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 200).containsEntry(2L, 150);
    }
}
