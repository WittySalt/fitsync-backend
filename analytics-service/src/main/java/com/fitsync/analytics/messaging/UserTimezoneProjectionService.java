package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            cacheInvalidationService.evictAllForUser(event.userId());
        }
        recordProcessedEvent(event);
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

    private void recordProcessedEvent(FitSyncDomainEvent<?> event) {
        AnalyticsProcessedEventEntity processedEvent = new AnalyticsProcessedEventEntity();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEvent.setUserId(event.userId());
        processedEvent.setProcessedAt(event.occurredAt() == null ? Instant.now(clock) : event.occurredAt());
        processedEventRepository.save(processedEvent);
    }
}
