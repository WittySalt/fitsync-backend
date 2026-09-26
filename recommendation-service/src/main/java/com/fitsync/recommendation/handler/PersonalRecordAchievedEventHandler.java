package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps only the LATEST personal record on the context (the full tally lives in
 * analytics) so R6 can celebrate it while it's fresh. The payload lists every record
 * broken in the session; the first one is kept — the producer orders weight first,
 * which is also the most celebration-worthy.
 */
@Service
public class PersonalRecordAchievedEventHandler implements DomainEventConsumer<PersonalRecordAchievedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public PersonalRecordAchievedEventHandler(RecommendationContextStore contextStore,
                                              ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<PersonalRecordAchievedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        PersonalRecordAchievedPayload payload = event.payload();
        if (payload == null || payload.records().isEmpty()) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        PersonalRecordAchievedPayload.RecordAchievement record = payload.records().get(0);
        context.setLastPrExercise(payload.exerciseNameSnapshot());
        context.setLastPrRecordType(record.recordType());
        context.setLastPrValue(record.value());
        context.setLastPrAt(payload.achievedAt() != null ? payload.achievedAt() : event.occurredAt());
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }
}
