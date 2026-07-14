package com.fitsync.recommendation.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.config.RecommendationProperties;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import com.fitsync.recommendation.service.ContextHashing;
import com.fitsync.recommendation.service.rules.RecommendationTypes;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * RC-6 (A1) — the one live AI feature: a short weekly coaching narrative over the
 * analytics recap numbers. Compute-in-code, model-phrases (README §3): the prompt gets
 * only rule-computed facts, never raw data. Generation is asynchronous (a recap event
 * or refresh only enqueues it), gated by the §5.1 change-detection hash + a one-week
 * TTL, and degrades silently — on a missing client or any API failure the cached
 * rule/template results simply keep serving. The read path never touches this class.
 */
@Service
public class WeeklyNarrativeService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyNarrativeService.class);

    private static final String SYSTEM_PROMPT = """
            You are FitSync's fitness coach writing a user's weekly recap.
            Write 2-3 warm, encouraging sentences summarizing their week and one concrete, \
            gentle suggestion for next week.
            Use ONLY the numbers provided in the message - never invent or recompute numbers.
            No greetings, no sign-offs, no emojis, no bullet points: just the narrative text.""";

    private final RecommendationContextRepository contextRepository;
    private final RecommendationResultRepository resultRepository;
    private final ObjectProvider<ClaudeClient> claudeClientProvider;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "weekly-narrative-generator");
        thread.setDaemon(true);
        return thread;
    });

    public WeeklyNarrativeService(RecommendationContextRepository contextRepository,
                                  RecommendationResultRepository resultRepository,
                                  ObjectProvider<ClaudeClient> claudeClientProvider,
                                  ObjectMapper objectMapper,
                                  RecommendationProperties properties,
                                  Clock clock) {
        this.contextRepository = contextRepository;
        this.resultRepository = resultRepository;
        this.claudeClientProvider = claudeClientProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Enqueue narrative generation for a user. Returns immediately; a no-op when the
     * AI tier is disabled, so callers never need to know whether AI exists.
     */
    public void generateAsync(UUID userId) {
        if (claudeClientProvider.getIfAvailable() == null) {
            return;
        }
        executor.execute(() -> generateNow(userId));
    }

    /**
     * The synchronous core (runs on the generator thread; also the test entry point).
     * Skips without a model call when there is nothing to phrase or the stored
     * narrative is still fresh for an unchanged context.
     */
    public void generateNow(UUID userId) {
        ClaudeClient claudeClient = claudeClientProvider.getIfAvailable();
        if (claudeClient == null) {
            return;
        }
        Optional<RecommendationContextEntity> maybeContext = contextRepository.findById(userId);
        if (maybeContext.isEmpty() || maybeContext.get().getRecentWeekStart() == null) {
            return;
        }
        RecommendationContextEntity context = maybeContext.get();
        String contextHash = ContextHashing.hash(context);
        Instant now = Instant.now(clock);
        Optional<RecommendationResultEntity> existing = resultRepository
                .findByUserIdAndType(userId, RecommendationTypes.WEEKLY_NARRATIVE);
        if (existing.isPresent() && isStillFresh(existing.get(), contextHash, now)) {
            return;
        }

        Map<String, Object> facts = weeklyFacts(context);
        try {
            String narrative = claudeClient.complete(SYSTEM_PROMPT, promptFor(facts));
            if (narrative.isBlank()) {
                return;
            }
            persist(userId, existing, facts, narrative, contextHash, now);
        } catch (RuntimeException ex) {
            // Graceful degradation (§5.1): the rule/template results keep serving.
            // Values are sanitized inline (CRLF, CWE-117) — findsecbugs only accepts
            // replace() directly in the dataflow to the sink.
            log.warn("event=weekly_narrative_generation_failed userId={} reason={}",
                    String.valueOf(userId).replace("\r", "_").replace("\n", "_"),
                    String.valueOf(ex.getMessage()).replace("\r", "_").replace("\n", "_"));
        }
    }

    private boolean isStillFresh(RecommendationResultEntity existing, String contextHash, Instant now) {
        boolean unexpired = existing.getExpiresAt() == null || !now.isAfter(existing.getExpiresAt());
        return unexpired && existing.getContextHash() != null
                && MessageDigest.isEqual(
                        contextHash.getBytes(StandardCharsets.UTF_8),
                        existing.getContextHash().getBytes(StandardCharsets.UTF_8));
    }

    /** Rule-computed facts only — the model phrases, it never sees raw data. */
    private static Map<String, Object> weeklyFacts(RecommendationContextEntity context) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("weekStart", context.getRecentWeekStart());
        facts.put("workoutCount", context.getRecentWorkoutCount());
        facts.put("workoutMinutes", context.getRecentWorkoutMinutes());
        facts.put("activeDays", context.getRecentActiveDays());
        if (context.getWorkoutDaysPerWeek() != null) {
            facts.put("targetDaysPerWeek", context.getWorkoutDaysPerWeek());
        }
        if (context.getCurrentGoalType() != null) {
            facts.put("goalType", context.getCurrentGoalType());
        }
        if (context.getCurrentStreakDays() != null) {
            facts.put("streakDays", context.getCurrentStreakDays());
        }
        if (context.getFocusAreas() != null) {
            facts.put("focusAreas", context.getFocusAreas());
        }
        return facts;
    }

    private String promptFor(Map<String, Object> facts) {
        try {
            return "This week's facts as JSON:\n" + objectMapper.writeValueAsString(facts);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize weekly facts", ex);
        }
    }

    private void persist(UUID userId,
                         Optional<RecommendationResultEntity> existing,
                         Map<String, Object> facts,
                         String narrative,
                         String contextHash,
                         Instant now) {
        RecommendationResultEntity result = existing.orElseGet(() -> {
            RecommendationResultEntity created = new RecommendationResultEntity();
            created.setId(UUID.randomUUID());
            created.setUserId(userId);
            created.setType(RecommendationTypes.WEEKLY_NARRATIVE);
            return created;
        });
        result.setSource(RecommendationSource.AI);
        result.setPayload(payloadJson(facts, narrative));
        result.setGeneratedAt(now);
        result.setExpiresAt(now.plus(properties.ai().narrativeTtl()));
        result.setContextHash(contextHash);
        resultRepository.save(result);
    }

    private String payloadJson(Map<String, Object> facts, String narrative) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facts", facts);
        payload.put("narrative", narrative);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize narrative payload", ex);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
