package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.springframework.stereotype.Service;

@Service
public class MealUpdatedEventConsumerImpl implements DomainEventConsumer<MealUpdatedPayload> {

    private final MealNutritionProjectionService projectionService;

    public MealUpdatedEventConsumerImpl(MealNutritionProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<MealUpdatedPayload> event) {
        MealUpdatedPayload payload = event.payload();
        projectionService.upsertMeal(
                event,
                payload == null ? null : payload.mealId(),
                payload == null ? null : payload.mealType(),
                payload == null ? null : payload.consumedAt(),
                payload == null ? null : payload.totalCalories(),
                payload == null ? null : payload.totalProteinGrams(),
                payload == null ? null : payload.totalCarbsGrams(),
                payload == null ? null : payload.totalFatGrams(),
                payload == null ? null : payload.itemCount(),
                payload == null ? null : payload.foodGroupCalories());
    }
}
