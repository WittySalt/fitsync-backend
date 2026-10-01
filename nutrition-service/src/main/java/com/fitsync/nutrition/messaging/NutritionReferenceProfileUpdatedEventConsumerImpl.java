package com.fitsync.nutrition.messaging;

import java.time.Instant;
import java.util.Set;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NutritionReferenceProfileUpdatedPayload;
import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the reference sex and the age band on the preference projection. Each event carries both values in
 * full, so applying one twice leaves the same row and the consumer needs no record of the events it has seen.
 */
@Service
public class NutritionReferenceProfileUpdatedEventConsumerImpl
        implements DomainEventConsumer<NutritionReferenceProfileUpdatedPayload> {

    // A value this service does not know is stored as unknown instead of failing the event, so that a newer
    // producer cannot make the listener retry the same message forever.
    private static final Set<String> KNOWN_SEXES = Set.of("FEMALE", "MALE", "BLENDED");
    private static final Set<String> KNOWN_BANDS = Set.of("19-30", "31-50", "51-70", "71+");

    private final NutritionPreferenceProjectionRepository repository;

    public NutritionReferenceProfileUpdatedEventConsumerImpl(NutritionPreferenceProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<NutritionReferenceProfileUpdatedPayload> event) {
        NutritionReferenceProfileUpdatedPayload payload = event.payload();
        NutritionPreferenceProjectionEntity entity = repository.findById(event.userId())
                .orElseGet(() -> {
                    NutritionPreferenceProjectionEntity created = new NutritionPreferenceProjectionEntity();
                    created.setUserId(event.userId());
                    return created;
                });
        entity.setReferenceSex(payload.referenceSex() != null && KNOWN_SEXES.contains(payload.referenceSex()) ? payload.referenceSex() : null);
        entity.setAgeBand(payload.ageBand() != null && KNOWN_BANDS.contains(payload.ageBand()) ? payload.ageBand() : null);
        entity.setProjectionUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        repository.save(entity);
    }
}
