package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.guardrail.CoachVoice;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
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

        ToolResult result = new ReadRecentTrainingTool(client, objectMapper,
                new UserCalendar(mock(RecommendationContextRepository.class), clock))
                .invoke(CALL, objectMapper.createObjectNode());

        assertThat(result.observation())
                .contains("Friday 2026-10-02 | Easy run | CARDIO | 36 min | warm-up ran 5 minutes against 5 planned; "
                        + "steady ran 26 minutes against 20 planned; cool-down skipped")
                .contains("Thursday 2026-10-01 | Intervals | HIIT | 22 min | work intervals: 1 of 2 done")
                .endsWith("Wednesday 2026-09-30 | Push | STRENGTH | 50 min");
    }

    @Test
    @DisplayName("a 06:45 Singapore session is dated the Singapore day, not the UTC day before")
    void morningSessionInSingaporeIsDatedLocally() {
        // Mobility flow started 06:45 and ended 07:05 on Thursday 8 October in Singapore, which is
        // 22:45 and 23:05 UTC on Wednesday 7 October. Asked at 02:19 on Friday 9 October in Singapore.
        RecommendationContextRepository repository = mock(RecommendationContextRepository.class);
        RecommendationContextEntity stored = new RecommendationContextEntity();
        stored.setTimezone("Asia/Singapore");
        UUID user = UUID.randomUUID();
        when(repository.findById(user)).thenReturn(Optional.of(stored));
        when(client.completedSessions(anyString(), any(), anyInt())).thenReturn("""
                {"content":[{"startedAt":"2026-10-07T22:45:00Z","completedAt":"2026-10-07T23:05:00Z",
                  "name":"Mobility flow","workoutType":"MOBILITY","durationMinutes":20,"entries":[]}]}
                """);
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T18:19:00Z"), ZoneOffset.UTC);

        // No zone from the client, as with the evaluation harness, so the stored profile zone applies.
        ToolResult result = new ReadRecentTrainingTool(client, objectMapper, new UserCalendar(repository, clock))
                .invoke(new CoachCallContext(user, "caller-credential", UUID.randomUUID()),
                        objectMapper.createObjectNode());

        assertThat(result.observation())
                .isEqualTo("Thursday 2026-10-08 | Mobility flow | MOBILITY | 20 min");
        // The seven days are 3 to 9 October in Singapore, so the window opens at 16:00 UTC on 2 October.
        verify(client).completedSessions("caller-credential", Instant.parse("2026-10-02T16:00:00Z"), 20);
    }

    @Test
    @DisplayName("the zone the client sent dates the session when nothing is stored")
    void clientZoneDatesTheSession() {
        when(client.completedSessions(anyString(), any(), anyInt())).thenReturn("""
                {"content":[{"completedAt":"2026-10-07T23:05:00Z","name":"Mobility flow",
                  "workoutType":"MOBILITY","durationMinutes":20,"entries":[]}]}
                """);
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T18:19:00Z"), ZoneOffset.UTC);
        CoachCallContext singapore = new CoachCallContext(UUID.randomUUID(), "caller-credential",
                UUID.randomUUID(), "Asia/Singapore");

        ToolResult result = new ReadRecentTrainingTool(client, objectMapper,
                new UserCalendar(mock(RecommendationContextRepository.class), clock))
                .invoke(singapore, objectMapper.createObjectNode());

        assertThat(result.observation()).startsWith("Thursday 2026-10-08 | Mobility flow");
    }

    @Test
    @DisplayName("an instant is turned into the local day, and text that is not an instant into nothing")
    void localDateOfAnInstant() {
        ZoneId singapore = ZoneId.of("Asia/Singapore");

        assertThat(UserCalendar.localDate("2026-10-07T22:45:00Z", singapore)).contains(LocalDate.of(2026, 10, 8));
        assertThat(UserCalendar.localDate("2026-10-08T06:45:00+08:00", ZoneOffset.UTC))
                .contains(LocalDate.of(2026, 10, 7));
        assertThat(UserCalendar.localDate("2026-10-08", singapore)).isEmpty();
        assertThat(UserCalendar.localDate(null, singapore)).isEmpty();
    }
}
