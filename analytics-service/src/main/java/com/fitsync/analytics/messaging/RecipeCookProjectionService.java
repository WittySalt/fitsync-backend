package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserCooksEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserCooksRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects RecipeCooked events into the per-user cook counter the kitchen badges read. Each event is
 * applied once, through the processed-events table. The counter only grows: a cook deleted later is not
 * taken off, matching the rule that a badge is never taken back.
 */
@Service
public class RecipeCookProjectionService {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserCooksRepository cooksRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final Clock clock;

    public RecipeCookProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                       AnalyticsUserCooksRepository cooksRepository,
                                       AnalyticsCacheInvalidationService cacheInvalidationService,
                                       Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.cooksRepository = cooksRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.clock = clock;
    }

    @Transactional
    public void applyCook(FitSyncDomainEvent<?> event, Instant finishedAt) {
        if (event == null || event.eventId() == null || processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (event.userId() == null) {
            ProcessedEvents.record(processedEventRepository, event, clock);
            return;
        }
        UUID userId = event.userId();
        Instant cookedAt = cookedAt(finishedAt, event.occurredAt());
        AnalyticsUserCooksEntity cooks = cooksRepository.findById(userId).orElseGet(() -> newRow(userId));
        cooks.setCooks((cooks.getCooks() == null ? 0 : cooks.getCooks()) + 1);
        if (cooks.getFirstCookAt() == null || cookedAt.isBefore(cooks.getFirstCookAt())) {
            cooks.setFirstCookAt(cookedAt);
        }
        if (cooks.getLastCookAt() == null || cookedAt.isAfter(cooks.getLastCookAt())) {
            cooks.setLastCookAt(cookedAt);
        }
        cooksRepository.save(cooks);
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(userId);
    }

    private Instant cookedAt(Instant finishedAt, Instant occurredAt) {
        if (finishedAt != null) {
            return finishedAt;
        }
        return occurredAt != null ? occurredAt : Instant.now(clock);
    }

    private static AnalyticsUserCooksEntity newRow(UUID userId) {
        AnalyticsUserCooksEntity created = new AnalyticsUserCooksEntity();
        created.setUserId(userId);
        created.setCooks(0);
        return created;
    }
}
