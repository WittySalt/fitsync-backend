package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.RecipeCookedPayload;
import org.springframework.stereotype.Service;

/** Handles RecipeCooked by adding one to the user's cook counter. */
@Service
public class RecipeCookedEventConsumerImpl implements DomainEventConsumer<RecipeCookedPayload> {

    private final RecipeCookProjectionService projectionService;

    public RecipeCookedEventConsumerImpl(RecipeCookProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<RecipeCookedPayload> event) {
        RecipeCookedPayload payload = event.payload();
        projectionService.applyCook(event, payload == null ? null : payload.finishedAt());
    }
}
