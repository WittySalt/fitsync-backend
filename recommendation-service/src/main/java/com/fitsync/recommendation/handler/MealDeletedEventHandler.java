package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A meal deletion invalidates the running today-totals ({@code MealDeleted} carries only
 * the meal id, so the removed calories are unknown here). The handler only marks the
 * context stale; the resolver recomputes from fresher sources on the next read.
 */
@Service
public class MealDeletedEventHandler implements DomainEventConsumer<MealDeletedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public MealDeletedEventHandler(RecommendationContextStore contextStore,
                                   ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<MealDeletedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        contextStore.saveStale(contextStore.getOrCreate(event.userId()));
        processedEventGuard.record(event);
    }
}
