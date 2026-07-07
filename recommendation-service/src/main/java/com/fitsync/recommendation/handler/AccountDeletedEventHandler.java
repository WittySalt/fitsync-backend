package com.fitsync.recommendation.handler;

import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges everything recommendation-service holds for a deleted account: the context row,
 * all cached results, and the user's processed-event ledger (only the AccountDeleted
 * event itself is kept, for idempotency).
 */
@Service
public class AccountDeletedEventHandler implements DomainEventConsumer<AccountDeletedPayload> {

    private final RecommendationContextStore contextStore;
    private final RecommendationResultRepository resultRepository;
    private final ProcessedEventGuard processedEventGuard;

    public AccountDeletedEventHandler(RecommendationContextStore contextStore,
                                      RecommendationResultRepository resultRepository,
                                      ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.resultRepository = resultRepository;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<AccountDeletedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        UUID userId = event.userId();
        contextStore.delete(userId);
        resultRepository.deleteByUserId(userId);
        processedEventGuard.purgeForUser(userId);
        processedEventGuard.record(event);
    }
}
