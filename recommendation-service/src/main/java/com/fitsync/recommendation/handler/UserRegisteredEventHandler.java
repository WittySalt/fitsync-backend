package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cold-start trigger (R5): registering creates the user's context row (stale, empty) so
 * the cold-start defaults rule has something to fire against before any other signal.
 */
@Service
public class UserRegisteredEventHandler implements DomainEventConsumer<UserRegisteredPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public UserRegisteredEventHandler(RecommendationContextStore contextStore,
                                      ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserRegisteredPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        contextStore.saveStale(contextStore.getOrCreate(event.userId()));
        processedEventGuard.record(event);
    }
}
