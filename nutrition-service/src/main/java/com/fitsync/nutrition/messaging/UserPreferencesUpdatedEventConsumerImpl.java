package com.fitsync.nutrition.messaging;

import java.time.Instant;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesUpdatedEventConsumerImpl implements DomainEventConsumer<UserPreferencesUpdatedPayload> {

    private final NutritionPreferenceProjectionRepository repository;

    public UserPreferencesUpdatedEventConsumerImpl(NutritionPreferenceProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserPreferencesUpdatedPayload> event) {
        UserPreferencesUpdatedPayload payload = event.payload();
        NutritionPreferenceProjectionEntity entity = repository.findById(event.userId())
                .orElseGet(() -> {
                    NutritionPreferenceProjectionEntity created = new NutritionPreferenceProjectionEntity();
                    created.setUserId(event.userId());
                    return created;
                });
        entity.setPreferredUnitsCode(payload.preferredUnits());
        entity.setDietaryPreferenceCode(payload.dietaryPreference());
        entity.setWorkoutDaysPerWeek(payload.workoutDaysPerWeek());
        entity.setNotificationsEnabled(payload.notificationsEnabled());
        entity.setProjectionUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        repository.save(entity);
    }
}
