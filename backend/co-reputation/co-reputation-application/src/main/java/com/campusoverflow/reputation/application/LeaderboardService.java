package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排行榜：周/月/学期榜来自 Redis ZSET，总榜直接读账户表。 */
@Service
public class LeaderboardService {

    private final LeaderboardStore store;
    private final ReputationAccountRepository accounts;
    private final IdentityApi identity;
    private final ProcessedEventStore processed;
    private final Clock clock;

    public LeaderboardService(LeaderboardStore store, ReputationAccountRepository accounts, IdentityApi identity,
                              ProcessedEventStore processed, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.identity = identity;
        this.processed = processed;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(ReputationChangedEvent e) {
        store.increment(e.userId(), e.delta(), e.occurredAt());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> top(LeaderboardPeriod period, int limit) {
        if (period == LeaderboardPeriod.ALL) {
            var accs = accounts.topByReputation(limit);
            var users = identity.findUsers(accs.stream().map(ReputationAccount::userId).toList());
            List<LeaderboardEntryView> result = new ArrayList<>();
            int rank = 1;
            for (var acc : accs) {
                var u = users.get(acc.userId());
                result.add(new LeaderboardEntryView(rank++, acc.userId(),
                        u != null ? u.displayName() : "", u != null && u.verified(), acc.reputation()));
            }
            return result;
        }
        var entries = store.top(period, clock.instant(), limit);
        var users = identity.findUsers(entries.stream().map(LeaderboardStore.Entry::userId).toList());
        List<LeaderboardEntryView> result = new ArrayList<>();
        int rank = 1;
        for (var en : entries) {
            var u = users.get(en.userId());
            result.add(new LeaderboardEntryView(rank++, en.userId(),
                    u != null ? u.displayName() : "", u != null && u.verified(), en.score()));
        }
        return result;
    }
}
