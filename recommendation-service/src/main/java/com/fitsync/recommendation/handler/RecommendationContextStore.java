package com.fitsync.recommendation.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import org.springframework.stereotype.Service;

/**
 * Shared access to the per-user {@code recommendation_context} row. Handlers fetch via
 * {@link #getOrCreate} (any event may be a user's first) and persist via
 * {@link #saveStale}, which flips the stale flag — the signal for the resolver funnel
 * to recompute on the next read/refresh. Nothing here ever calls AI.
 */
@Service
public class RecommendationContextStore {

    private final RecommendationContextRepository contextRepository;
    private final Clock clock;

    public RecommendationContextStore(RecommendationContextRepository contextRepository, Clock clock) {
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    public RecommendationContextEntity getOrCreate(UUID userId) {
        return contextRepository.findById(userId).orElseGet(() -> {
            RecommendationContextEntity context = new RecommendationContextEntity();
            context.setUserId(userId);
            return context;
        });
    }

    public void saveStale(RecommendationContextEntity context) {
        context.setStale(true);
        context.setLastAssembledAt(Instant.now(clock));
        contextRepository.save(context);
    }

    public void delete(UUID userId) {
        contextRepository.findById(userId).ifPresent(contextRepository::delete);
    }
}
