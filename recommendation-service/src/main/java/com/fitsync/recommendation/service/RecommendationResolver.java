package com.fitsync.recommendation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.config.RecommendationProperties;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import com.fitsync.recommendation.service.ai.WeeklyNarrativeService;
import com.fitsync.recommendation.service.rules.NarrativeTemplates;
import com.fitsync.recommendation.service.rules.RuleEngine;
import com.fitsync.recommendation.service.rules.RuleOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RC-4 — the read-path decision funnel (README §5.1):
 * <pre>cache (fresh) → rule → template → (last resort) AI</pre>
 *
 * Reads serve straight from {@code recommendation_result}; only when the context is
 * stale or results expired does the funnel drop to the rule stage — synchronous and
 * cheap (pure functions + one upsert). The AI stage never runs inline: a refresh only
 * <em>enqueues</em> the weekly-narrative generation, which is hash-gated and writes
 * into the same cache, so the read path never waits on a model.
 */
@Service
public class RecommendationResolver {

    private final RecommendationContextRepository contextRepository;
    private final RecommendationResultRepository resultRepository;
    private final RuleEngine ruleEngine;
    private final NarrativeTemplates narrativeTemplates;
    private final WeeklyNarrativeService weeklyNarrativeService;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final Clock clock;

    public RecommendationResolver(RecommendationContextRepository contextRepository,
                                  RecommendationResultRepository resultRepository,
                                  RuleEngine ruleEngine,
                                  NarrativeTemplates narrativeTemplates,
                                  WeeklyNarrativeService weeklyNarrativeService,
                                  ObjectMapper objectMapper,
                                  RecommendationProperties properties,
                                  Clock clock) {
        this.contextRepository = contextRepository;
        this.resultRepository = resultRepository;
        this.ruleEngine = ruleEngine;
        this.narrativeTemplates = narrativeTemplates;
        this.weeklyNarrativeService = weeklyNarrativeService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The read path: serve the cached results, dropping to the rule stage first if the
     * cache is not fresh. A user without any context yet gets an empty list.
     */
    @Transactional
    public List<RecommendationResultEntity> resolve(UUID userId) {
        Optional<RecommendationContextEntity> context = contextRepository.findById(userId);
        if (context.isEmpty()) {
            return List.of();
        }
        List<RecommendationResultEntity> cached = sorted(resultRepository.findByUserId(userId));
        if (isFresh(context.get(), cached)) {
            return cached;
        }
        refresh(context.get());
        return sorted(resultRepository.findByUserId(userId));
    }

    /** One recommendation type, through the same funnel. */
    @Transactional
    public Optional<RecommendationResultEntity> resolve(UUID userId, String type) {
        return resolve(userId).stream()
                .filter(result -> result.getType().equals(type))
                .findFirst();
    }

    /**
     * Recompute the rule stage for a user (the {@code POST /refresh} entry point).
     * Rules run synchronously; a future AI stage is enqueued from here, never inline.
     */
    @Transactional
    public void refresh(UUID userId) {
        contextRepository.findById(userId).ifPresent(this::refresh);
    }

    private void refresh(RecommendationContextEntity context) {
        Instant now = Instant.now(clock);
        String contextHash = ContextHashing.hash(context);
        List<RuleOutcome> outcomes = ruleEngine.evaluateAll(context);

        for (RuleOutcome outcome : outcomes) {
            RecommendationResultEntity result = resultRepository
                    .findByUserIdAndType(context.getUserId(), outcome.type())
                    .orElseGet(() -> newResult(context.getUserId(), outcome.type()));
            result.setSource(RecommendationSource.RULE);
            result.setPayload(payloadJson(outcome));
            result.setGeneratedAt(now);
            result.setExpiresAt(now.plus(properties.ruleResultTtl()));
            result.setContextHash(contextHash);
            resultRepository.save(result);
        }
        pruneUnfiredRuleResults(context.getUserId(), outcomes);
        pruneExpiredAiResults(context.getUserId(), now);

        context.setStale(false);
        context.setContextHash(contextHash);
        context.setLastAssembledAt(now);
        contextRepository.save(context);

        // AI stage: enqueue only — hash-gated inside the service, never inline here.
        weeklyNarrativeService.generateAsync(context.getUserId());
    }

    /** A rule that no longer fires must not keep serving yesterday's advice. */
    private void pruneUnfiredRuleResults(UUID userId, List<RuleOutcome> outcomes) {
        Set<String> firedTypes = outcomes.stream().map(RuleOutcome::type).collect(Collectors.toSet());
        resultRepository.findByUserId(userId).stream()
                .filter(result -> result.getSource() == RecommendationSource.RULE)
                .filter(result -> !firedTypes.contains(result.getType()))
                .forEach(resultRepository::delete);
    }

    /**
     * An expired AI narrative must not pin the cache stale forever (regeneration is
     * async and may be disabled); drop it and let the AI stage rewrite it when it can.
     */
    private void pruneExpiredAiResults(UUID userId, Instant now) {
        resultRepository.findByUserId(userId).stream()
                .filter(result -> result.getSource() == RecommendationSource.AI)
                .filter(result -> result.getExpiresAt() != null && now.isAfter(result.getExpiresAt()))
                .forEach(resultRepository::delete);
    }

    private boolean isFresh(RecommendationContextEntity context, List<RecommendationResultEntity> cached) {
        if (context.isStale() || cached.isEmpty()) {
            return false;
        }
        Instant now = Instant.now(clock);
        return cached.stream()
                .noneMatch(result -> result.getExpiresAt() != null && now.isAfter(result.getExpiresAt()));
    }

    private static RecommendationResultEntity newResult(UUID userId, String type) {
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setType(type);
        return result;
    }

    private String payloadJson(RuleOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facts", outcome.facts());
        payload.put("narrative", narrativeTemplates.render(outcome));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize recommendation payload", ex);
        }
    }

    private static List<RecommendationResultEntity> sorted(List<RecommendationResultEntity> results) {
        return results.stream()
                .sorted(Comparator.comparing(RecommendationResultEntity::getType))
                .toList();
    }
}
