package com.fitsync.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
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
 * cheap (pure functions + one upsert). The AI stage is intentionally absent here:
 * RC-6 plugs it in asynchronously behind the same cache, so the read path never waits
 * on a model. Rule results carry the context hash so the future AI stage can skip
 * regeneration when the underlying context hasn't materially changed (§5.1
 * change-detection).
 */
@Service
public class RecommendationResolver {

    private final RecommendationContextRepository contextRepository;
    private final RecommendationResultRepository resultRepository;
    private final RuleEngine ruleEngine;
    private final NarrativeTemplates narrativeTemplates;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final Clock clock;

    public RecommendationResolver(RecommendationContextRepository contextRepository,
                                  RecommendationResultRepository resultRepository,
                                  RuleEngine ruleEngine,
                                  NarrativeTemplates narrativeTemplates,
                                  ObjectMapper objectMapper,
                                  RecommendationProperties properties,
                                  Clock clock) {
        this.contextRepository = contextRepository;
        this.resultRepository = resultRepository;
        this.ruleEngine = ruleEngine;
        this.narrativeTemplates = narrativeTemplates;
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
        String contextHash = hash(context);
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

        context.setStale(false);
        context.setContextHash(contextHash);
        context.setLastAssembledAt(now);
        contextRepository.save(context);
    }

    /** A rule that no longer fires must not keep serving yesterday's advice. */
    private void pruneUnfiredRuleResults(UUID userId, List<RuleOutcome> outcomes) {
        Set<String> firedTypes = outcomes.stream().map(RuleOutcome::type).collect(Collectors.toSet());
        resultRepository.findByUserId(userId).stream()
                .filter(result -> result.getSource() == RecommendationSource.RULE)
                .filter(result -> !firedTypes.contains(result.getType()))
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

    /**
     * Change-detection hash (§5.1): a digest over every rule-relevant context field.
     * Stored on each result so the future AI stage can skip regeneration when nothing
     * material changed.
     */
    private static String hash(RecommendationContextEntity context) {
        String canonical = String.join("|",
                String.valueOf(context.getCurrentGoalType()),
                String.valueOf(context.getCurrentGoalTargetValue()),
                String.valueOf(context.getCurrentGoalTargetUnit()),
                String.valueOf(context.getCurrentGoalTargetDate()),
                String.valueOf(context.getGoalBaselineWeightKg()),
                String.valueOf(context.getGoalBaselineOn()),
                String.valueOf(context.getHeightCm()),
                String.valueOf(context.getActivityLevel()),
                String.valueOf(context.getLatestWeightKg()),
                String.valueOf(context.getLatestBodyFatRate()),
                String.valueOf(context.getWorkoutDaysPerWeek()),
                String.valueOf(context.getDietaryPreference()),
                String.valueOf(context.getFocusAreas()),
                String.valueOf(context.getEquipmentInventory()),
                String.valueOf(context.getRecentWorkoutCount()),
                String.valueOf(context.getRecentWorkoutMinutes()),
                String.valueOf(context.getRecentActiveDays()),
                String.valueOf(context.getRecentWeekStart()),
                String.valueOf(context.getTodayDate()),
                String.valueOf(context.getTodayCaloriesConsumed()),
                String.valueOf(context.getTodayProteinGrams()),
                String.valueOf(context.getCurrentStreakDays()),
                String.valueOf(context.getTimezone()));
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static List<RecommendationResultEntity> sorted(List<RecommendationResultEntity> results) {
        return results.stream()
                .sorted(Comparator.comparing(RecommendationResultEntity::getType))
                .toList();
    }
}
