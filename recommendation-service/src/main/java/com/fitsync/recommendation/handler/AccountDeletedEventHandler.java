package com.fitsync.recommendation.handler;

import java.util.List;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges everything recommendation-service holds for a deleted account: the context row,
 * all cached results, whatever every registered {@link AccountDataPurger} holds (the coach
 * layer's cached page readings, today), and the user's processed-event ledger (only the
 * AccountDeleted event itself is kept, for idempotency).
 *
 * <p>{@code purgers} is a plain list rather than a direct dependency on any one package's
 * repository, because this handler must never import the coach package (see
 * {@code CoachDependencyDirectionTests}) — a consumer that reached into coach could end up
 * awaiting a model call inside this Kafka listener. Spring collects every
 * {@link AccountDataPurger} bean into the list regardless of which package defines it, so the
 * dependency points the only way the architecture allows: from coach down to the projection.
 */
@Service
public class AccountDeletedEventHandler implements DomainEventConsumer<AccountDeletedPayload> {

    private final RecommendationContextStore contextStore;
    private final RecommendationResultRepository resultRepository;
    private final List<AccountDataPurger> purgers;
    private final ProcessedEventGuard processedEventGuard;

    public AccountDeletedEventHandler(RecommendationContextStore contextStore,
                                      RecommendationResultRepository resultRepository,
                                      List<AccountDataPurger> purgers,
                                      ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.resultRepository = resultRepository;
        this.purgers = List.copyOf(purgers);
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
        purgers.forEach(purger -> purger.purgeForUser(userId));
        processedEventGuard.purgeForUser(userId);
        processedEventGuard.record(event);
    }
}
