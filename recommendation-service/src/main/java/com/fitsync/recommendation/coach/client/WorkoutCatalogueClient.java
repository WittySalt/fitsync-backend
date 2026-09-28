package com.fitsync.recommendation.coach.client;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final ObjectMapper objectMapper;

    public WorkoutCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties,
                                      ObjectMapper objectMapper) {
        super(builder, properties.workoutBaseUrl(), "workout");
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

    public String completedSessions(String bearerToken, LocalDate from, int size) {
        Map<String, Object> params = params();
        params.put("status", "COMPLETED");
        params.put("from", from + "T00:00:00Z");
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
     * The pre-computed training facts a workout-page reading is built from.
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
        params.put("timezone", timezone);
        String body = get(TRAINING_INSIGHT, params, bearerToken);
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readValue(body, TrainingInsight.class);
        } catch (JsonProcessingException ex) {
            // Sanitised inline against CRLF injection (CWE-117): findsecbugs only accepts replace()
            // directly in the dataflow to the log sink, the same way WeeklyNarrativeService does it.
            // The parser's own message can quote the body, so it is left out of the log.
            LOG.warn("event=training_insight_unreadable");
            return null;
        }
    }
}
