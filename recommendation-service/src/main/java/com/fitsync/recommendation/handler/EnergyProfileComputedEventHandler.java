package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EV-3 — user-service computed the energy profile with the real PII inputs (which never
 * leave that service) and sent only the results; the TDEE becomes the context's energy
 * target, which R1 prefers over its own neutral-constant estimate.
 */
@Service
public class EnergyProfileComputedEventHandler implements DomainEventConsumer<EnergyProfileComputedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public EnergyProfileComputedEventHandler(RecommendationContextStore contextStore,
                                             ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<EnergyProfileComputedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        EnergyProfileComputedPayload payload = event.payload();
        if (payload == null || payload.tdeeCalories() == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        context.setEnergyTargetCalories(payload.tdeeCalories());
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }
}
