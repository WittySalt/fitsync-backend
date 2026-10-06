package com.fitsync.recommendation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.domain.UserTimeZones;
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
 * <pre>cache (fresh) → rule → template</pre>
 *
 * Reads serve straight from {@code recommendation_result}; only when the context is
 * stale or results expired does the funnel drop to the rule stage — synchronous and
 * cheap (pure functions + one upsert). The A1 weekly narrative is no longer generated:
 * the Progress weekly review is the narrative, and the coach dock no longer shows these
 * results. The read API stays for the admin console and the tests, and a narrative
 * stored by an older build is pruned on the next refresh.
 */
@Service
public class RecommendationResolver {

    private final RecommendationContextRepository contextRepository;
    private final RecommendationResultRepository resultRepository;
    private final RuleEngine ruleEngine;
    private final TodayIntakeSource todayIntakeSource;
    private final NarrativeTemplates narrativeTemplates;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final Clock clock;

    public RecommendationResolver(RecommendationContextRepository contextRepository,
                                  RecommendationResultRepository resultRepository,
                                  RuleEngine ruleEngine,
                                  TodayIntakeSource todayIntakeSource,
                                  NarrativeTemplates narrativeTemplates,
                                  ObjectMapper objectMapper,
                                  RecommendationProperties properties,
                                  Clock clock) {
        this.contextRepository = contextRepository;
        this.resultRepository = resultRepository;
        this.ruleEngine = ruleEngine;
        this.todayIntakeSource = todayIntakeSource;
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
        return resolveWithToken(userId, null);
    }

    /**
     * As {@link #resolve(UUID)}, with the caller's token so today's intake is read from the
     * Nutrition Diary when the rules run. Without a token the nutrition rule states targets only.
     */
    @Transactional
    public List<RecommendationResultEntity> resolveWithToken(UUID userId, String bearerToken) {
        Optional<RecommendationContextEntity> context = contextRepository.findById(userId);
        if (context.isEmpty()) {
            return List.of();
        }
        List<RecommendationResultEntity> cached = sorted(resultRepository.findByUserId(userId));
        if (isFresh(context.get(), cached)) {
            return cached;
        }
        refresh(context.get(), bearerToken);
        return sorted(resultRepository.findByUserId(userId));
    }

    /** One recommendation type, through the same funnel. */
    @Transactional
    public Optional<RecommendationResultEntity> resolve(UUID userId, String type) {
        return resolveType(userId, type, null);
    }

    /** One recommendation type, reading today's intake with the caller's token. */
    @Transactional
    public Optional<RecommendationResultEntity> resolveType(UUID userId, String type, String bearerToken) {
        return resolveWithToken(userId, bearerToken).stream()
                .filter(result -> result.getType().equals(type))
                .findFirst();
    }

    /**
     * Recompute the rule stage for a user (the {@code POST /refresh} entry point).
     * Rules run synchronously; a future AI stage is enqueued from here, never inline.
     */
    @Transactional
    public void refresh(UUID userId) {
        refresh(userId, null);
    }

    /** As {@link #refresh(UUID)}, reading today's intake with the caller's token. */
    @Transactional
    public void refresh(UUID userId, String bearerToken) {
        contextRepository.findById(userId).ifPresent(context -> refresh(context, bearerToken));
    }

    private void refresh(RecommendationContextEntity context, String bearerToken) {
        Instant now = Instant.now(clock);
        String contextHash = ContextHashing.hash(context);
        List<RuleOutcome> outcomes = ruleEngine.evaluateAll(context, todayIntake(context, bearerToken));

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
        pruneRetiredAiResults(context.getUserId());

        context.setStale(false);
        context.setContextHash(contextHash);
        context.setLastAssembledAt(now);
        contextRepository.save(context);
    }

    /** Today's intake from the diary, read once for this refresh. */
    private TodayIntake todayIntake(RecommendationContextEntity context, String bearerToken) {
        if (bearerToken == null) {
            return TodayIntake.unknown();
        }
        ZoneId zone = UserTimeZones.resolve(context.getTimezone());
        return todayIntakeSource.read(bearerToken, LocalDate.now(clock.withZone(zone)), zone);
    }

    /** A rule that no longer fires must not keep serving yesterday's advice. */
    private void pruneUnfiredRuleResults(UUID userId, List<RuleOutcome> outcomes) {
        Set<String> firedTypes = outcomes.stream().map(RuleOutcome::type).collect(Collectors.toSet());
        resultRepository.findByUserId(userId).stream()
                .filter(result -> result.getSource() == RecommendationSource.RULE)
                .filter(result -> !firedTypes.contains(result.getType()))
                .forEach(resultRepository::delete);
    }

    /** The A1 narrative is retired; one an older build stored is not served again. */
    private void pruneRetiredAiResults(UUID userId) {
        resultRepository.findByUserId(userId).stream()
                .filter(result -> result.getSource() == RecommendationSource.AI)
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
