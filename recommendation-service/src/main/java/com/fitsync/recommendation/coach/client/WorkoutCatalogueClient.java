package com.fitsync.recommendation.coach.client;

import java.time.LocalDate;
import java.util.Map;

import com.fitsync.recommendation.coach.config.CoachProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads the exercise catalogue and session history from workout-service. */
@Component
public class WorkoutCatalogueClient extends CatalogueClient {

    private static final String EXERCISES = "/api/v1/workouts/exercises";
    private static final String SESSIONS = "/api/v1/workouts/sessions";

    public WorkoutCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties) {
        super(builder, properties.workoutBaseUrl(), "workout");
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

    public String completedSessions(String bearerToken, LocalDate from, int size) {
        Map<String, Object> params = params();
        params.put("status", "COMPLETED");
        params.put("from", from + "T00:00:00Z");
        params.put("page", 0);
        params.put("size", size);
        return get(SESSIONS, params, bearerToken);
    }
}
