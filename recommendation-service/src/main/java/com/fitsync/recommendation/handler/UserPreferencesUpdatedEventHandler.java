package com.fitsync.recommendation.handler;

import java.util.List;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects workout/diet preferences into the context — inputs for R2 weekly adherence
 * and R5 cold-start defaults (and later the A2/A3 prompts). List fields are stored as a
 * comma-joined snapshot; order is preserved as sent.
 */
@Service
public class UserPreferencesUpdatedEventHandler implements DomainEventConsumer<UserPreferencesUpdatedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public UserPreferencesUpdatedEventHandler(RecommendationContextStore contextStore,
                                              ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserPreferencesUpdatedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        UserPreferencesUpdatedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        context.setWorkoutDaysPerWeek(payload.workoutDaysPerWeek());
        context.setDietaryPreference(payload.dietaryPreference());
        context.setFocusAreas(joinOrNull(payload.focusAreas()));
        context.setEquipmentInventory(joinOrNull(payload.equipmentInventory()));
        if (payload.timezone() != null) {
            context.setTimezone(payload.timezone());
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    private static String joinOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }
}
