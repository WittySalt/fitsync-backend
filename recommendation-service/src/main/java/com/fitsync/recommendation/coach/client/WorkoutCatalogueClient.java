package com.fitsync.recommendation.coach.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.reading.TrainingInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads the exercise catalogue and session history from workout-service. */
@Component
public class WorkoutCatalogueClient extends CatalogueClient {

    private static final Logger LOG = LoggerFactory.getLogger(WorkoutCatalogueClient.class);

    private static final String EXERCISES = "/api/v1/workouts/exercises";
    private static final String SESSIONS = "/api/v1/workouts/sessions";
    private static final String PLANS = "/api/v1/workouts/plans";
    private static final String TRAINING_INSIGHT = "/api/v1/workouts/insights/training";
    private static final String WEEKLY_INSIGHT = "/api/v1/workouts/insights/weekly";
    private static final String TRAINING_PROGRESS = "/api/v1/workouts/progress/training";
    private static final String ACTIVITY_PROGRESS = "/api/v1/workouts/progress/activities/";
    private static final String EXERCISE_PROGRESS = "/api/v1/workouts/progress/exercises/";

    private final ObjectMapper objectMapper;

    public WorkoutCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties,
                                      ObjectMapper objectMapper) {
        super(builder, properties.apiBaseUrl(), "workout");
        this.objectMapper = objectMapper;
    }

    /**
     * Structured filters first, free text only to break the tie. The catalogue carries muscle group,
     * equipment and difficulty as real columns, so most of the narrowing happens before any text
     * matching, which is why this needs no semantic index.
     */
    public String searchExercises(String bearerToken,
                                  String muscleGroup,
                                  String equipment,
                                  String difficulty,
                                  String query,
                                  int size) {
        Map<String, Object> params = params();
        params.put("primaryMuscleGroup", muscleGroup);
        params.put("equipment", equipment);
        params.put("difficulty", difficulty);
        params.put("q", query);
        params.put("page", 0);
        params.put("size", size);
        return get(EXERCISES, params, bearerToken);
    }

    /**
     * One exercise by identifier, which is how a draft's references are checked.
     *
     * <p>The search endpoint cannot do this: it filters, and a draft needs to know whether one
     * specific row exists and is still active. Returns null for anything the user cannot see, which
     * for the validator is the same answer as "there is no such exercise", and correctly so.
     */
    public String exercise(String bearerToken, UUID exerciseId) {
        return get(EXERCISES + "/" + exerciseId, params(), bearerToken);
    }

    /** Completed sessions since {@code from}, the start of the user's first day in the window. */
    public String completedSessions(String bearerToken, Instant from, int size) {
        Map<String, Object> params = params();
        params.put("status", "COMPLETED");
        params.put("from", from.toString());
        params.put("page", 0);
        params.put("size", size);
        return get(SESSIONS, params, bearerToken);
    }

    /**
     * Creates a plan from an accepted draft. The request body is workout-service's own contract,
     * built by the mapper, so the coach writes exactly what a user would through the plan editor.
     */
    public String createPlan(String bearerToken, Object createPlanRequest) {
        return post(PLANS, createPlanRequest, bearerToken);
    }

    /** Starts a session from a plan, which is the moment a draft becomes something to train from. */
    public String startPlan(String bearerToken, UUID planId) {
        return post(PLANS + "/" + planId + "/start", Map.of(), bearerToken);
    }

    /**
     * The person's workout in progress as workout-service reports it, or null when there is none or
     * the service could not be reached. Asking also makes workout-service close a workout that was
     * left, so a forgotten one does not count.
     */
    public String activeSession(String bearerToken) {
        return get(SESSIONS + "/active", params(), bearerToken);
    }

    /**
     * The person's current plan with its days, entries and coverage, or null when there is none or the
     * service could not be reached.
     */
    public String activePlan(String bearerToken) {
        return get(PLANS + "/active", params(), bearerToken);
    }

    /** Archives a plan, which is how a plan this coach created is taken back. */
    public void archivePlan(String bearerToken, UUID planId) {
        delete(PLANS + "/" + planId, bearerToken);
    }

    /**
     * The current week as workout-service counts it: the sessions completed since Monday and
     * their minutes. Null when the service could not be reached.
     */
    public String weeklyInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        return get(WEEKLY_INSIGHT, params, bearerToken, timezone);
    }

    /** The weekly insight of the Monday week that holds {@code date}, or null when it could not be read. */
    public String weeklyInsight(String bearerToken, String timezone, LocalDate date) {
        Map<String, Object> params = params();
        params.put("date", date);
        return get(WEEKLY_INSIGHT, params, bearerToken, timezone);
    }

    /**
     * The main lifts' estimated best singles from the weekly insight, empty when it could not be read.
     * The weekly insight carries them as {@code lifts} with the best single at the start and now.
     */
    public List<TrainingInsight.BestSingle> weeklyBestSingles(String bearerToken, String timezone) {
        String body = weeklyInsight(bearerToken, timezone);
        if (body == null) {
            return List.of();
        }
        try {
            JsonNode lifts = objectMapper.readTree(body).path("lifts");
            List<TrainingInsight.BestSingle> singles = new ArrayList<>();
            for (JsonNode lift : lifts) {
                singles.add(objectMapper.treeToValue(lift, TrainingInsight.BestSingle.class));
            }
            return singles;
        } catch (JsonProcessingException ex) {
            LOG.warn("event=weekly_insight_unreadable");
            return List.of();
        }
    }

    /**
     * The pre-computed training facts a workout-page reading is built from, over the current week so far
     * (Monday to today in the caller's zone), or period NONE while the week holds no session.
     *
     * <p>Parsed here, unlike this class's other reads, because a reading has nothing further to do
     * with the raw JSON: every caller wants the typed shape. A null return covers both "the service
     * could not be reached" (the base class already turns that into null) and "the body came back
     * but was not valid JSON for this contract" (workout-service is a different codebase, and a
     * malformed body is exactly the kind of failure a coach page should degrade in front of rather
     * than crash on). Callers treat both the same way: the upstream is unavailable.
     */
    public TrainingInsight trainingInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        // The workout reading covers the current week so far (Monday to today in the caller's
        // zone), never the last full week, so the coach talks about the week the person is in.
        params.put("thisWeek", true);
        String body = get(TRAINING_INSIGHT, params, bearerToken, timezone);
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readValue(body, TrainingInsight.class);
        } catch (JsonProcessingException ex) {
            // Sanitised inline against CRLF injection (CWE-117): findsecbugs only accepts replace()
            // directly in the dataflow to the log sink, as elsewhere in this service.
            // The parser's own message can quote the body, so it is left out of the log.
            LOG.warn("event=training_insight_unreadable");
            return null;
        }
    }
    /**
     * The Training page's figures for one period, as JSON, or null when workout-service could not be
     * read. Only the caller's token and zone are forwarded; the body weight header the page sends is
     * not, so the lift ratios are whatever workout-service already knows.
     */
    public String trainingProgress(String bearerToken, String timezone, LocalDate from, LocalDate to) {
        return get(TRAINING_PROGRESS, period(from, to), bearerToken, timezone);
    }

    /**
     * One activity's page for one period, as JSON, or null when it could not be read.
     *
     * @param key an activity key already checked to be capital letters, underscores and colons
     */
    public String activityProgress(String bearerToken, String timezone, String key, LocalDate from, LocalDate to) {
        return get(ACTIVITY_PROGRESS + key, period(from, to), bearerToken, timezone);
    }

    /** One exercise's page for one period, as JSON, or null when it could not be read. */
    public String exerciseProgress(String bearerToken, String timezone, UUID exerciseId, LocalDate from,
                                   LocalDate to) {
        return get(EXERCISE_PROGRESS + exerciseId, period(from, to), bearerToken, timezone);
    }

    private static Map<String, Object> period(LocalDate from, LocalDate to) {
        Map<String, Object> params = params();
        params.put("from", from);
        params.put("to", to);
        return params;
    }
}
