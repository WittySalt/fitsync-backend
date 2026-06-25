package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserTimezoneProjectionTests {

    @Autowired
    private DomainEventConsumer<UserPreferencesUpdatedPayload> userPreferencesUpdatedConsumer;

    @Autowired
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;

    @BeforeEach
    void clearData() {
        processedEventRepository.deleteAll();
        dashboardSnapshotRepository.deleteAll();
    }

    @Test
    void userPreferencesUpdatedProjectsTimezoneIntoDashboardSnapshot() {
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-17T16:00:00Z");

        userPreferencesUpdatedConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserPreferencesUpdated",
                1,
                occurredAt,
                "user-service",
                userId,
                new UserPreferencesUpdatedPayload(
                        "100001",
                        4,
                        "300001",
                        true,
                        List.of(),
                        List.of(),
                        "Asia/Singapore")));

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getTimezone()).isEqualTo("Asia/Singapore");
                    assertThat(snapshot.getGeneratedAt()).isEqualTo(occurredAt);
                });
        assertThat(processedEventRepository.count()).isEqualTo(1L);
    }
}
