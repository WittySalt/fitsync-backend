package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the user's timezone on the dashboard snapshot. Every "today" and "this week" in analytics is
 * resolved in that zone. A missing or invalid zone is ignored, so the last valid one is kept.
 */
@Service
public class UserTimezoneProjectionService {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;
    private final Clock clock;

    public UserTimezoneProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                         AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                         AnalyticsCacheInvalidationService cacheInvalidationService,
                                         DashboardSnapshotInitializer dashboardSnapshotInitializer,
                                         Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
        this.clock = clock;
    }

    @Transactional
    public void projectTimezone(FitSyncDomainEvent<?> event, String timezone) {
        if (event == null || processedEventRepository.existsById(event.eventId())) {
            return;
        }

        String normalizedTimezone = normalizeTimezone(timezone);
        if (normalizedTimezone != null) {
            Instant generatedAt = event.occurredAt() == null ? Instant.now(clock) : event.occurredAt();
            dashboardSnapshotInitializer.insertIfMissing(event.userId(), generatedAt);
            AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(event.userId())
                    .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));
            snapshot.setTimezone(normalizedTimezone);
            snapshot.setGeneratedAt(generatedAt);
            dashboardSnapshotRepository.save(snapshot);
            cacheInvalidationService.evictForUser(event.userId());
        }
        ProcessedEvents.record(processedEventRepository, event, clock);
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        String trimmed = timezone.trim();
        try {
            return ZoneId.of(trimmed).getId();
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
