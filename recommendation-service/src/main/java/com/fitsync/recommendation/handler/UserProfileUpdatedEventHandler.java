package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects profile inputs for the local TDEE estimate (heightCm, activityLevel — R1) and
 * the user's timezone. The event carries no age/sex by design; an exact TDEE needs a
 * user-service contract read (README §2, fact 3).
 */
@Service
public class UserProfileUpdatedEventHandler implements DomainEventConsumer<UserProfileUpdatedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public UserProfileUpdatedEventHandler(RecommendationContextStore contextStore,
                                          ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserProfileUpdatedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        UserProfileUpdatedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        if (payload.heightCm() != null) {
            context.setHeightCm(payload.heightCm());
        }
        if (payload.activityLevel() != null) {
            context.setActivityLevel(payload.activityLevel());
        }
        if (payload.timezone() != null) {
            context.setTimezone(payload.timezone());
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }
}
