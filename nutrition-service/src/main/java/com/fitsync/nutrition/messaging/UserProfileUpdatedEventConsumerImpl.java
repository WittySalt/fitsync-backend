package com.fitsync.nutrition.messaging;

import java.time.Instant;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the person's sex and birth date on the preference projection. Each event carries the whole profile, so
 * applying one twice leaves the same row and the consumer needs no record of the events it has seen.
 */
@Service
public class UserProfileUpdatedEventConsumerImpl implements DomainEventConsumer<UserProfileUpdatedPayload> {

    private final NutritionPreferenceProjectionRepository repository;

    public UserProfileUpdatedEventConsumerImpl(NutritionPreferenceProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserProfileUpdatedPayload> event) {
        UserProfileUpdatedPayload payload = event.payload();
        NutritionPreferenceProjectionEntity entity = repository.findById(event.userId())
                .orElseGet(() -> {
                    NutritionPreferenceProjectionEntity created = new NutritionPreferenceProjectionEntity();
                    created.setUserId(event.userId());
                    return created;
                });
        entity.setSex(payload.gender());
        entity.setBirthDate(payload.birthDate());
        entity.setProjectionUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        repository.save(entity);
    }
}
