package com.fitsync.nutrition.messaging;

import java.time.Instant;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the newest weight on the preference projection. An event with no weight, or one for a weigh-in older
 * than the stored one, is ignored, so late or replayed events cannot bring back an old weight.
 */
@Service
public class BodyMetricsRecordedEventConsumerImpl implements DomainEventConsumer<BodyMetricsRecordedPayload> {

    private final NutritionPreferenceProjectionRepository repository;

    public BodyMetricsRecordedEventConsumerImpl(NutritionPreferenceProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<BodyMetricsRecordedPayload> event) {
        BodyMetricsRecordedPayload payload = event.payload();
        if (payload.weightKg() == null || payload.measuredAt() == null) {
            return;
        }
        NutritionPreferenceProjectionEntity entity = repository.findById(event.userId())
                .orElseGet(() -> {
                    NutritionPreferenceProjectionEntity created = new NutritionPreferenceProjectionEntity();
                    created.setUserId(event.userId());
                    return created;
                });
        if (entity.getWeightMeasuredAt() != null && payload.measuredAt().isBefore(entity.getWeightMeasuredAt())) {
            return;
        }
        entity.setWeightKg(payload.weightKg());
        entity.setWeightMeasuredAt(payload.measuredAt());
        entity.setProjectionUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        repository.save(entity);
    }
}
