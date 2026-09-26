package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the streak length from the analytics milestone event — input for the R4
 * consistency nudge.
 */
@Service
public class StreakMilestoneReachedEventHandler implements DomainEventConsumer<StreakMilestoneReachedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public StreakMilestoneReachedEventHandler(RecommendationContextStore contextStore,
                                              ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<StreakMilestoneReachedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        StreakMilestoneReachedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        context.setCurrentStreakDays(payload.streakDays());
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }
}
