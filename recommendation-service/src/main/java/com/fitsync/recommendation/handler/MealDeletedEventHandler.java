package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A meal deletion only marks the context stale, so cached recommendations refresh on the next
 * read. Today's intake is read from the Nutrition Diary at that point, which already leaves the
 * deleted meal out.
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
