package com.fitsync.recommendation.coach.draft;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.UserProfileClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.tool.UserCalendar;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Gathers the facts the validator judges against.
 *
 * <p>Separated from the validator so that validation stays a pure function. This half talks to
 * three services and a projection and is therefore the part that can fail; the half that decides
 * whether a draft is acceptable cannot, and can be tested exhaustively without any of this.
 *
 * <p>The reads are independent, so they run together on the coach's pool. They are also allowed to
 * come back empty. An unreachable questionnaire means no injury exclusions rather than a failed
 * draft, which is the right trade when the alternative is refusing to help; the gap it leaves is
 * narrow, because the rules that matter most are the ones checking the exercises themselves.
 */
@Component
public class DraftContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(DraftContextAssembler.class);

    /** How far back "trained yesterday" reaches. Two days of sessions, filtered to yesterday's. */
    private static final int RECENT_SESSION_DAYS = 2;
    private static final int RECENT_SESSION_PAGE = 10;
    /** A cap on how many exercise lookups one recovery check may cost. */
    private static final int MAX_RECENT_LOOKUPS = 20;
    /** Used when a user has no history to infer a session length from. */
    private static final int DEFAULT_TARGET_MINUTES = 45;
    private static final int MIN_TARGET_MINUTES = 20;
    private static final int MAX_TARGET_MINUTES = 120;

    private final WorkoutCatalogueClient workoutClient;
    private final UserProfileClient userClient;
    private final RecommendationContextRepository contextRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Clock clock;

    public DraftContextAssembler(WorkoutCatalogueClient workoutClient,
                                 UserProfileClient userClient,
                                 RecommendationContextRepository contextRepository,
                                 ObjectMapper objectMapper,
                                 @Qualifier("coachToolExecutor") ExecutorService executor,
                                 Clock clock) {
        this.workoutClient = workoutClient;
        this.userClient = userClient;
        this.contextRepository = contextRepository;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * @param candidates the exercises offered to the model, pre-resolved so the catalogue map covers
     *                   at least everything a well-behaved draft can reference
     */
    public DraftContext assemble(CoachCallContext call, List<CatalogueExercise> candidates) {
        CompletableFuture<Set<String>> recent = CompletableFuture
                .supplyAsync(() -> recentMuscleGroups(call), executor)
                .exceptionally(failure -> {
                    LOG.warn("Recent training could not be read for the draft context", failure);
                    return Set.of();
                });
        CompletableFuture<Set<String>> injuries = CompletableFuture
                .supplyAsync(() -> injuryAreas(call), executor)
                .exceptionally(failure -> {
                    LOG.warn("Readiness questionnaire could not be read for the draft context", failure);
                    return Set.of();
                });

        Map<UUID, CatalogueExercise> catalogue = new HashMap<>();
        for (CatalogueExercise candidate : candidates == null ? List.<CatalogueExercise>of() : candidates) {
            catalogue.put(candidate.exerciseId(), candidate);
        }

        return new DraftContext(
                catalogue,
                equipmentInventory(call.userId()),
                recent.join(),
                injuries.join(),
                targetMinutes(call.userId()));
    }

    /**
     * Resolves an identifier the draft used that was not in the candidate list.
     *
     * <p>A model will occasionally reference a real exercise it saw in an earlier turn. Looking it
     * up rather than rejecting it on the spot means the validator judges the exercise rather than
     * the model's memory; an identifier that resolves to nothing still fails, which is the case that
     * matters.
     */
    public java.util.Optional<CatalogueExercise> resolve(CoachCallContext call, UUID exerciseId) {
        String body = workoutClient.exercise(call.bearerToken(), exerciseId);
        return body == null ? java.util.Optional.empty() : readExercise(body);
    }

    /**
     * What the user trains with, translated into the catalogue's vocabulary.
     *
     * <p>The projection stores the onboarding answer as user-service reference codes, which share
     * no values with the equipment names workout-service puts on an exercise.
     */
    private Set<String> equipmentInventory(UUID userId) {
        return contextRepository.findById(userId)
                .map(entity -> EquipmentVocabulary.toCatalogueEquipment(entity.getEquipmentInventory()))
                .orElseGet(Set::of);
    }

    /**
     * The session length this user actually trains for.
     *
     * <p>Their recent average rather than a stated preference, because no such preference is
     * collected and inventing a field to hold one would be a worse answer than reading what they
     * do. Clamped, so a single three-hour outlier does not become the target.
     */
    private int targetMinutes(UUID userId) {
        return contextRepository.findById(userId)
                .map(entity -> {
                    Integer minutes = entity.getRecentWorkoutMinutes();
                    Integer count = entity.getRecentWorkoutCount();
                    if (minutes == null || count == null || count <= 0 || minutes <= 0) {
                        return Integer.valueOf(DEFAULT_TARGET_MINUTES);
                    }
                    int average = minutes / count;
                    return Integer.valueOf(Math.max(MIN_TARGET_MINUTES, Math.min(average, MAX_TARGET_MINUTES)));
                })
                .orElseGet(() -> Integer.valueOf(DEFAULT_TARGET_MINUTES))
                .intValue();
    }

    /**
     * Muscle groups trained yesterday.
     *
     * <p>Sessions carry exercise identifiers, not muscle groups, so each distinct exercise from
     * yesterday is looked up. Bounded, because this runs inside a turn a user is waiting on and a
     * pathological session should cost a rule rather than the draft.
     */
    private Set<String> recentMuscleGroups(CoachCallContext call) {
        // Yesterday is the user's yesterday: a session at 06:45 in Singapore is 22:45 UTC the day before.
        UserCalendar calendar = new UserCalendar(contextRepository, clock);
        ZoneId zone = calendar.zone(call);
        LocalDate today = calendar.today(zone);
        LocalDate yesterday = today.minusDays(1);
        String body = workoutClient.completedSessions(call.bearerToken(),
                today.minusDays(RECENT_SESSION_DAYS - 1L).atStartOfDay(zone).toInstant(), RECENT_SESSION_PAGE);
        if (body == null) {
            return Set.of();
        }

        Set<UUID> exerciseIds = new LinkedHashSet<>();
        try {
            for (JsonNode session : objectMapper.readTree(body).path("content")) {
                if (!UserCalendar.localDate(session.path("completedAt").asText(null), zone)
                        .filter(yesterday::equals).isPresent()) {
                    continue;
                }
                for (JsonNode logged : session.path("exercises")) {
                    String id = logged.path("exerciseId").asText(null);
                    if (id != null && exerciseIds.size() < MAX_RECENT_LOOKUPS) {
                        exerciseIds.add(UUID.fromString(id));
                    }
                }
            }
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            LOG.warn("Recent sessions could not be read for the recovery rule", ex);
            return Set.of();
        }

        Set<String> groups = new LinkedHashSet<>();
        for (CompletableFuture<java.util.Optional<CatalogueExercise>> pending : lookupAll(call, exerciseIds)) {
            pending.join().map(CatalogueExercise::primaryMuscleGroup)
                    .filter(group -> !group.isBlank())
                    .ifPresent(group -> groups.add(group.toUpperCase(Locale.ROOT)));
        }
        return groups;
    }

    private List<CompletableFuture<java.util.Optional<CatalogueExercise>>> lookupAll(CoachCallContext call,
                                                                                     Set<UUID> exerciseIds) {
        List<CompletableFuture<java.util.Optional<CatalogueExercise>>> pending = new ArrayList<>();
        for (UUID exerciseId : exerciseIds) {
            pending.add(CompletableFuture
                    .supplyAsync(() -> resolve(call, exerciseId), executor)
                    .exceptionally(failure -> java.util.Optional.empty()));
        }
        return pending;
    }

    private Set<String> injuryAreas(CoachCallContext call) {
        String body = userClient.healthReadiness(call.bearerToken());
        if (body == null) {
            return Set.of();
        }
        try {
            Set<String> areas = new LinkedHashSet<>();
            for (JsonNode area : objectMapper.readTree(body).path("injuryAreas")) {
                String value = area.asText("");
                if (!value.isBlank()) {
                    areas.add(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
                }
            }
            return areas;
        } catch (JsonProcessingException ex) {
            LOG.warn("Readiness questionnaire could not be parsed", ex);
            return Set.of();
        }
    }

    /** Turns one exercise response into the narrow shape the rules use. */
    private java.util.Optional<CatalogueExercise> readExercise(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String id = node.path("exerciseId").asText(null);
            if (id == null) {
                return java.util.Optional.empty();
            }
            List<String> secondary = new ArrayList<>();
            for (JsonNode group : node.path("secondaryMuscleGroups")) {
                secondary.add(group.asText(""));
            }
            return java.util.Optional.of(new CatalogueExercise(
                    UUID.fromString(id),
                    node.path("name").asText(""),
                    node.path("primaryMuscleGroup").asText(""),
                    secondary,
                    node.path("equipment").asText(""),
                    // Exact, not case-insensitive. The field is a serialised enum, so it is
                    // always upper case, and a loose comparison here would only widen what counts
                    // as active without making anything work that does not already.
                    "ACTIVE".equals(node.path("status").asText(""))));
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            LOG.warn("An exercise could not be read from the catalogue response", ex);
            return java.util.Optional.empty();
        }
    }

}
