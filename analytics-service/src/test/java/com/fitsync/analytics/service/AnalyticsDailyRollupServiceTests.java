package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

class AnalyticsDailyRollupServiceTests {

    @Test
    void processesDashboardUsersInBatchesAndContinuesAfterSingleUserFailure() {
        AnalyticsUserDashboardSnapshotRepository snapshotRepository =
                mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsDailyRollupUserProcessor userProcessor = mock(AnalyticsDailyRollupUserProcessor.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-18T00:05:00Z"), ZoneOffset.UTC);

        UUID firstUser = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID failingUser = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID lastUser = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(snapshotRepository.findSnapshotUserIds(PageRequest.of(0, 200)))
                .thenReturn(new SliceImpl<>(List.of(firstUser, failingUser), PageRequest.of(0, 200), true));
        when(snapshotRepository.findSnapshotUserIds(PageRequest.of(1, 200)))
                .thenReturn(new SliceImpl<>(List.of(lastUser), PageRequest.of(1, 200), false));
        doThrow(new IllegalStateException("boom"))
                .when(userProcessor).rollupUser(failingUser, Instant.parse("2026-06-18T00:05:00Z"));

        AnalyticsDailyRollupService service = new AnalyticsDailyRollupService(
                snapshotRepository, userProcessor, clock);

        AnalyticsDailyRollupService.RollupSummary summary = service.runDailyRollupOnce();

        assertThat(summary.processedUsers()).isEqualTo(2);
        assertThat(summary.failedUsers()).isEqualTo(1);

        InOrder order = inOrder(userProcessor);
        order.verify(userProcessor).rollupUser(firstUser, Instant.parse("2026-06-18T00:05:00Z"));
        order.verify(userProcessor).rollupUser(failingUser, Instant.parse("2026-06-18T00:05:00Z"));
        order.verify(userProcessor).rollupUser(lastUser, Instant.parse("2026-06-18T00:05:00Z"));
    }
}
