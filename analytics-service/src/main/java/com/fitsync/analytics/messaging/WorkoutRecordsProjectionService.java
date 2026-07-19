package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects PersonalRecordAchieved events into the per-user personal-record tally used by the
 * achievements endpoint (PR_FIRST). The counter is append-only: workout deletions do not retract
 * records, matching the "ever achieved" semantics of achievements.
 */
@Service
public class WorkoutRecordsProjectionService {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final Clock clock;

    public WorkoutRecordsProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                           AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository,
                                           AnalyticsCacheInvalidationService cacheInvalidationService,
                                           Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.workoutRecordsRepository = workoutRecordsRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.clock = clock;
    }

    @Transactional
    public void applyPersonalRecords(FitSyncDomainEvent<?> event, int recordCount, Instant achievedAt) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (event.userId() == null || recordCount <= 0) {
            recordProcessedEvent(event);
            return;
        }

        AnalyticsUserWorkoutRecordsEntity records = workoutRecordsRepository.findById(event.userId())
                .orElseGet(() -> createRecordsRow(event.userId()));
        Instant recordedAt = resolveRecordedAt(achievedAt, event.occurredAt());
        records.setPersonalRecordCount(defaultInt(records.getPersonalRecordCount()) + recordCount);
        if (records.getFirstRecordAt() == null || recordedAt.isBefore(records.getFirstRecordAt())) {
            records.setFirstRecordAt(recordedAt);
        }
        if (records.getLastRecordAt() == null || recordedAt.isAfter(records.getLastRecordAt())) {
            records.setLastRecordAt(recordedAt);
        }
        workoutRecordsRepository.save(records);
        recordProcessedEvent(event);
        cacheInvalidationService.evictForUser(event.userId());
    }

    private AnalyticsUserWorkoutRecordsEntity createRecordsRow(UUID userId) {
        AnalyticsUserWorkoutRecordsEntity records = new AnalyticsUserWorkoutRecordsEntity();
        records.setUserId(userId);
        records.setPersonalRecordCount(0);
        return records;
    }

    private Instant resolveRecordedAt(Instant achievedAt, Instant occurredAt) {
        if (achievedAt != null) {
            return achievedAt;
        }
        if (occurredAt != null) {
            return occurredAt;
        }
        return Instant.now(clock);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
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
