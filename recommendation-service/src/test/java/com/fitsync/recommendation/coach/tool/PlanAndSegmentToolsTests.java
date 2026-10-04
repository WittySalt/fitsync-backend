package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.guardrail.CoachVoice;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The plan read and the session read in words: day kinds and coverage notes as plain sentences, and
 * time segments as minutes run against minutes planned. No code reaches the model.
 */
class PlanAndSegmentToolsTests {

    private static final CoachCallContext CALL = new CoachCallContext(UUID.randomUUID(), "caller-credential",
            UUID.randomUUID(), "UTC");

    private static final String PLAN = """
            {"name":"Strength and cardio, 4 days","days":[
              {"dayOfWeek":1,"kind":"STRENGTH","title":"Full body A","estimatedDurationMinutes":45,"entries":[
                {"entryType":"SET","exerciseNameSnapshot":"Goblet Squat","targetSets":3,"targetReps":10},
                {"entryType":"SET","exerciseNameSnapshot":"Dumbbell Bench Press","targetSets":3,"targetReps":10}]},
              {"dayOfWeek":2,"kind":"CARDIO","title":"Easy run","estimatedDurationMinutes":30,"effortTarget":3,"entries":[
                {"entryType":"SEGMENT","kind":"WARMUP","label":"Warm-up","plannedSeconds":300},
                {"entryType":"SEGMENT","kind":"STEADY","label":"Steady","plannedSeconds":1200},
                {"entryType":"SEGMENT","kind":"COOLDOWN","label":"Cool-down","plannedSeconds":300}]},
              {"dayOfWeek":6,"kind":"SPORT","title":"Badminton","estimatedDurationMinutes":60,"entries":[]}],
             "coverage":{"groups":[{"group":"CHEST","setsPerWeek":3.0},{"group":"LEGS","setsPerWeek":3.0}],
               "notes":[{"code":"CALVES_NOT_TRAINED","text":"Calves are not trained yet."},
                        {"code":"NO_VERTICAL_PULL","text":"No pull from above yet (lats)."}]}}
            """;

    private static final String SESSIONS = """
            {"content":[{"completedAt":"2026-10-02T08:00:00Z","name":"Easy run","workoutType":"CARDIO",
              "durationMinutes":36,"entries":[
                {"entryType":"SEGMENT","kind":"WARMUP","label":"Warm-up","plannedSeconds":300,"actualSeconds":300,"status":"DONE"},
                {"entryType":"SEGMENT","kind":"STEADY","label":"Steady","plannedSeconds":1200,"actualSeconds":1560,"status":"DONE"},
                {"entryType":"SEGMENT","kind":"COOLDOWN","label":"Cool-down","plannedSeconds":300,"status":"SKIPPED"}]},
              {"completedAt":"2026-10-01T08:00:00Z","name":"Intervals","workoutType":"HIIT","durationMinutes":22,"entries":[
                {"entryType":"SEGMENT","kind":"WORK","label":"Work 1","plannedSeconds":30,"actualSeconds":30,"status":"DONE"},
                {"entryType":"SEGMENT","kind":"EASY","label":"Easy 1","plannedSeconds":60,"actualSeconds":60,"status":"DONE"},
                {"entryType":"SEGMENT","kind":"WORK","label":"Work 2","plannedSeconds":30,"status":"PENDING"}]},
              {"completedAt":"2026-09-30T08:00:00Z","name":"Push","workoutType":"STRENGTH","durationMinutes":50,
               "entries":[{"entryType":"SET","exerciseNameSnapshot":"Bench Press"}]}]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkoutCatalogueClient client = mock(WorkoutCatalogueClient.class);

    @Test
    @DisplayName("the plan read gives day kinds, segments, rest days, sets a week and the notes in words")
    void planReadInWords() {
        when(client.activePlan(anyString())).thenReturn(PLAN);

        ToolResult result = new ReadPlanTool(client, objectMapper).invoke(CALL, objectMapper.createObjectNode());

        assertThat(result.observation())
                .contains("Plan: Strength and cardio, 4 days, 3 days a week.")
                .contains("Monday: Full body A, strength, 45 min, 2 moves: Goblet Squat 3 x 10, Dumbbell Bench Press 3 x 10.")
                .contains("Tuesday: Easy run, cardio, 30 min: warm-up 5 min, steady 20 min, cool-down 5 min; easy effort.")
                .contains("Saturday: Badminton, sport, 60 min.")
                .contains("Rest days: Wednesday, Thursday, Friday, Sunday.")
                .contains("Planned hard sets a week by muscle group: chest 3, legs 3.")
                .contains("What the plan leaves out: Calves are not trained yet. No pull from above yet (lats).")
                .doesNotContain("CALVES_NOT_TRAINED")
                .doesNotContain("STRENGTH");
        assertThat(CoachVoice.RAW_STATUS_WORD.matcher(result.observation()).find()).isFalse();
    }

    @Test
    @DisplayName("without a plan the read says so instead of failing the turn")
    void noPlan() {
        when(client.activePlan(anyString())).thenReturn(null);

        ToolResult result = new ReadPlanTool(client, objectMapper).invoke(CALL, objectMapper.createObjectNode());

        assertThat(result.summary()).isEqualTo("no plan");
    }

    @Test
    @DisplayName("the session read says how long each segment ran against its plan and counts the intervals")
    void sessionReadSaysSegmentsInWords() {
        when(client.completedSessions(anyString(), any(), anyInt())).thenReturn(SESSIONS);
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC);

        ToolResult result = new ReadRecentTrainingTool(client, objectMapper, clock)
                .invoke(CALL, objectMapper.createObjectNode());

        assertThat(result.observation())
                .contains("2026-10-02 | Easy run | CARDIO | 36 min | warm-up ran 5 minutes against 5 planned; "
                        + "steady ran 26 minutes against 20 planned; cool-down skipped")
                .contains("2026-10-01 | Intervals | HIIT | 22 min | work intervals: 1 of 2 done")
                .endsWith("2026-09-30 | Push | STRENGTH | 50 min");
    }
}
