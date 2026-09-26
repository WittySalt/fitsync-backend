package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A meal edit invalidates the running today-totals (the event carries the new totals but
 * not the old ones, so no delta is computable without per-meal contribution tracking —
 * deliberately left to analytics). The handler therefore only marks the context stale.
 */
@Service
public class MealUpdatedEventHandler implements DomainEventConsumer<MealUpdatedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public MealUpdatedEventHandler(RecommendationContextStore contextStore,
                                   ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<MealUpdatedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        contextStore.saveStale(contextStore.getOrCreate(event.userId()));
        processedEventGuard.record(event);
    }
}
