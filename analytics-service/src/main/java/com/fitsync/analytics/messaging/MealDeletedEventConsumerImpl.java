package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import org.springframework.stereotype.Service;

@Service
public class MealDeletedEventConsumerImpl implements DomainEventConsumer<MealDeletedPayload> {

    private final MealNutritionProjectionService projectionService;

    public MealDeletedEventConsumerImpl(MealNutritionProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<MealDeletedPayload> event) {
        MealDeletedPayload payload = event.payload();
        projectionService.deleteMeal(event, payload == null ? null : payload.mealId());
    }
}
