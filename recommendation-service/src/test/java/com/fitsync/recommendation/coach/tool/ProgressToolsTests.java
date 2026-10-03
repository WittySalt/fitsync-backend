package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.guardrail.CoachVoice;
import com.fitsync.recommendation.coach.orchestrator.AnswerValidator;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The five progress tools: each reads its endpoint with the caller's credential and zone, writes the
 * figures as plain facts in words the answer check accepts, and says so plainly when the endpoint
 * cannot be read.
 */
class ProgressToolsTests {

    private static final UUID USER = UUID.randomUUID();
    private static final String CALLER = "caller-credential";
    private static final CoachCallContext CALL = new CoachCallContext(USER, CALLER, UUID.randomUUID(), "Asia/Singapore");
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-28");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkoutCatalogueClient workout = mock(WorkoutCatalogueClient.class);
    private final AnalyticsProgressClient analytics = mock(AnalyticsProgressClient.class);
    private final UserCalendar calendar = new UserCalendar(mock(RecommendationContextRepository.class),
            Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));

    private final ReadTrainingProgressTool training = new ReadTrainingProgressTool(workout, objectMapper, calendar);
    private final ReadGoalProgressTool goal = new ReadGoalProgressTool(analytics, objectMapper, calendar);
    private final ReadFoodProgressTool food = new ReadFoodProgressTool(analytics, objectMapper, calendar);
    private final ReadExerciseProgressTool exercise = new ReadExerciseProgressTool(workout, objectMapper, calendar);
    private final ReadActivityProgressTool activity = new ReadActivityProgressTool(workout, objectMapper, calendar);

    private static final String TRAINING = """
            {"period":{"from":"2026-09-01","to":"2026-09-28"},"previous":{"from":"2026-08-04","to":"2026-08-31"},
             "hero":{"sessions":{"value":12,"previous":9},"minutes":{"value":540,"previous":410},
                     "load":{"value":3240,"previous":2950,"estimatedShare":0.25},
                     "records":{"value":2,"exercises":["Barbell Squat","Bench Press"]}},
             "highlights":[{"code":"LIFT_UP","title":"Your squat went up","series":[],"unit":"kg","context":"From 100 to 107.5 kg"}],
             "activities":[{"key":"STRENGTH","name":"Strength","sessions":8,"minutes":360,"cumulativeMinutes":[],
                            "summary":[{"text":"Squat 107.5 kg","trend":"UP"},{"text":"Sessions 2 a week","trend":"STEADY"}],
                            "context":"+1 h 10 on the 4 weeks before"},
                           {"key":"SPORT:BADMINTON","sessions":4,"minutes":180,"summary":[]}],
             "load":{"weeks":[],"fitness":[],"fatigue":[],"form":"TIRED"},
             "muscles":{"weeksCounted":4,"groups":[{"group":"CHEST","setsPerWeek":12.5,"exercises":[]}],
                        "usualRange":{"low":10,"high":20}},
             "lifts":[{"exerciseId":"6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb","name":"Barbell Squat",
                       "e1rm":[{"date":"2026-09-02","value":100,"isRecord":false},{"date":"2026-09-26","value":107.5,"isRecord":true}],
                       "bestSet":{"weightKg":95,"reps":4,"date":"2026-09-26"},"changePct":7.5,
                       "relative":{"ratio":1.34,"level":"INTERMEDIATE","nextLevel":"ADVANCED","nextAtKg":140},"bodyWeightKg":80}],
             "calendar":[],
             "records":[{"date":"2026-09-26","exerciseId":"6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb","name":"Barbell Squat",
                         "value":107.5,"unit":"KG","delta":2.5}]}""";

    private static final String GOAL = """
            {"goal":{"type":"WEIGHT","targetKg":75,"byDate":"2026-12-31"},
             "hero":{"nowKg":80.4,"changeKg":-1.2,"weeklyRate":{"value":-0.3,"previous":-0.25},
                     "projectedDate":{"value":"2027-01-15","previous":"2027-02-20"},"daysLeft":94},
             "highlights":[{"code":"RATE_IN_RANGE","title":"Your rate is in the usual range","series":[]}],
             "weight":{"daily":[],"weekly":[{"weekStart":"2026-09-21","avgKg":80.6,"weighIns":3}],"projection":[],
                       "method":"28-day average change carried forward"},
             "body":{"bodyFat":{"series":[],"start":22.5,"end":21.8,"context":""},"waist":{},"bmi":{}},
             "otherGoals":[{"type":"PROTEIN","target":140,"unit":"g","metDays":9,"loggedDays":20,
                            "days":[{"date":"2026-09-01","state":"NOT_LOGGED"}],"weeks":[]},
                           {"type":"WORKOUT_FREQUENCY","target":3,"unit":"SESSIONS","metDays":null,"loggedDays":null,
                            "weeks":[{"weekStart":"2026-09-21","value":4,"target":3}]}]}""";

    private static final String FOOD = """
            {"hero":{"eatenPerDay":{"value":2105.4,"previous":2230},"proteinPerDay":{"value":121,"previous":110},
                     "balancePerDay":{"value":-320,"daysLogged":20},"daysLogged":{"value":20,"previous":24,"days":28}},
             "highlights":[{"code":"PROTEIN_DRIFT","title":"Protein rose over the month","series":[]}],
             "energy":{},
             "macros":{"protein":{"firstWeek":104,"thisWeek":128,"target":140,"series":[]},"carbs":{},"fat":{},
                       "summary":[{"text":"Protein 128 g a day","trend":"UP"}]},
             "plate":{"current":[{"group":"VEGETABLES","grams":null,"share":0.12}],"previous":[],
                      "changes":[{"group":"VEGETABLES","deltaPoints":3}]},
             "meals":{"share":[{"mealType":"BREAKFAST","sharePct":24,"deltaPoints":1}]},
             "habits":{"waterMetDays":11,"fibreMetDays":6,"loggedDays":20}}""";

    private static final String EXERCISE = """
            {"exerciseId":"6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb","name":"Barbell Squat","primaryGroup":"LEGS",
             "secondaryGroups":["GLUTES"],"equipment":"BARBELL","firstSeen":"2026-03-02","sessions":8,
             "tiles":{"e1rm":{"value":107.5,"changeKg":7.5,"since":"2026-09-02"},
                      "heaviestSet":{"weightKg":95,"reps":4,"date":"2026-09-26"},"bestVolume":{"kg":2400,"date":"2026-09-19"},"records":2},
             "series":{},"history":[{"sessionId":"x","date":"2026-09-26","sets":[{"weightKg":95,"reps":4},{"weightKg":90,"reps":5}],"record":true}],
             "summary":[{"text":"Estimated max 107.5 kg","trend":"UP"}]}""";

    private static final String ACTIVITY = """
            {"key":"SPORT:BADMINTON","name":"Badminton","kind":"SPORT",
             "tiles":{"minutes":180,"sessions":4,"weeksInARow":3,"longestSession":{"minutes":60,"date":"2026-09-20"},
                      "usualTime":{"weekday":6,"hour":18},"effort":{"average":6.5,"first":6,"last":7}},
             "weekly":[{"weekStart":"2026-09-21","minutes":60,"sessions":1,"current":false}],
             "sessions":[],"whenGrid":[],"summary":[{"text":"60 minutes a week","trend":"STEADY"}],"context":""}""";

    private ObjectNode period() {
        return objectMapper.createObjectNode().put("from", FROM.toString()).put("to", TO.toString());
    }

    @Test
    @DisplayName("training progress is read with the caller's credential and zone and written as plain facts")
    void trainingProgress() {
        when(workout.trainingProgress(CALLER, "Asia/Singapore", FROM, TO)).thenReturn(TRAINING);

        ToolResult result = training.invoke(CALL, period());

        assertThat(result.observation())
                .contains("Training from 2026-09-01 to 2026-09-28. The period before is 2026-08-04 to 2026-08-31.")
                .contains("Sessions: 12, and 9 in the period before.")
                .contains("Minutes trained: 540, and 410 in the period before.")
                .contains("Training load (effort times minutes): 3,240, and 2,950 in the period before. 25% of it uses a default effort.")
                .contains("Records: 2, on Barbell Squat, Bench Press.")
                .contains("Form, from the last six weeks of load against last week's: tired.")
                .contains("- Your squat went up. From 100 to 107.5 kg")
                .contains("- Strength: 8 sessions, 360 minutes. +1 h 10 on the 4 weeks before. Squat 107.5 kg (going up); Sessions 2 a week (steady).")
                .contains("- Badminton: 4 sessions, 180 minutes.")
                .contains("- Barbell Squat: 107.5 kg on 2026-09-26, a change of 7.5% over the period, best set 95 kg for 4 reps, "
                        + "1.34 times body weight, level intermediate, the next level advanced starts at 140 kg.")
                .contains("over 4 weeks, where 10 to 20 is the usual range:")
                .contains("- chest: 12.5")
                .contains("- 2026-09-26 Barbell Squat: 107.5 kg, 2.5 above the record before");
        assertNoRawCodes(result.observation());
        assertThat(result.summary()).isEqualTo("training progress 2026-09-01 to 2026-09-28");
    }

    @Test
    @DisplayName("goal progress puts directions and codes into words")
    void goalProgress() {
        when(analytics.goalProgress(CALLER, "Asia/Singapore", FROM, TO)).thenReturn(GOAL);

        String text = goal.invoke(CALL, period()).observation();

        assertThat(text)
                .contains("Weight goal: 75 kg by 2026-12-31.")
                .contains("Weight now: 80.4 kg.")
                .contains("Change over the period: 1.2 kg down.")
                .contains("Weekly rate: 0.3 kg a week down, and 0.25 kg a week down in the period before.")
                .contains("Date the goal is reached at this rate: 2027-01-15, and 2027-02-20 at the rate of the period before.")
                .contains("Days left to the goal date: 94.")
                .contains("- week of 2026-09-21: 80.6 kg from 3 weigh-ins")
                .contains("Body fat: 22.5% at the start and 21.8% at the end.")
                .contains("- protein, target 140 g: met on 9 of 20 logged days.")
                .contains("- workout frequency, target 3 sessions; week of 2026-09-21 4.");
        assertNoRawCodes(text);
    }

    @Test
    @DisplayName("a goal of null is stated as no weight goal")
    void noWeightGoal() {
        when(analytics.goalProgress(anyString(), any(), any(), any())).thenReturn("{\"goal\":null,\"hero\":{}}");

        assertThat(goal.invoke(CALL, period()).observation()).contains("There is no weight goal set.");
    }

    @Test
    @DisplayName("food progress states calories, protein, balance and shares in words")
    void foodProgress() {
        when(analytics.foodProgress(CALLER, "Asia/Singapore", FROM, TO)).thenReturn(FOOD);

        String text = food.invoke(CALL, period()).observation();

        assertThat(text)
                .contains("Figures a day count only the days with food logged.")
                .contains("Calories eaten a day: 2,105.4 kcal, and 2,230 kcal in the period before.")
                .contains("Protein a day: 121 g, and 110 g in the period before.")
                .contains("Energy balance a day (eaten minus used): 320 kcal under what was used, over 20 logged days.")
                .contains("Days with food logged: 20 of 28, and 24 in the period before.")
                .contains("Protein: 104 g a day in the first week, 128 g a day in the latest week, target 140 g.")
                .contains("- vegetables: 12%, 3 points higher than the period before")
                .contains("Share of calories by meal: breakfast 24%.")
                .contains("Habits: water target met on 11 days, fibre target met on 6 days, of 20 logged days.");
        assertNoRawCodes(text);
    }

    @Test
    @DisplayName("exercise progress needs a UUID and states the estimated max, sets and records")
    void exerciseProgress() {
        UUID id = UUID.fromString("6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb");
        when(workout.exerciseProgress(CALLER, "Asia/Singapore", id, FROM, TO)).thenReturn(EXERCISE);

        String text = exercise.invoke(CALL, period().put("exerciseId", id.toString())).observation();

        assertThat(text)
                .contains("Exercise: Barbell Squat, trains legs.")
                .contains("Sessions with it: 8, first logged on 2026-03-02.")
                .contains("107.5 kg, 7.5 kg up since 2026-09-02.")
                .contains("Heaviest set: 95 kg for 4 reps on 2026-09-26.")
                .contains("Best session volume: 2,400 kg on 2026-09-19.")
                .contains("- 2026-09-26: 95 kg x 4, 90 kg x 5, a record");
        assertNoRawCodes(text);
        assertThatThrownBy(() -> exercise.invoke(CALL, period().put("exerciseId", "../admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("activity progress takes an activity key, upper-cased, and rejects anything else")
    void activityProgress() {
        when(workout.activityProgress(CALLER, "Asia/Singapore", "SPORT:BADMINTON", FROM, TO)).thenReturn(ACTIVITY);

        String text = activity.invoke(CALL, period().put("key", "sport:badminton")).observation();

        assertThat(text)
                .startsWith("Badminton (sport) from 2026-09-01 to 2026-09-28.")
                .contains("Sessions: 4, 180 minutes in all.")
                .contains("Weeks in a row with this activity: 3.")
                .contains("Longest session: 60 minutes on 2026-09-20.")
                .contains("Usual time: Saturday around 18:00.")
                .contains("Effort out of 10: 6.5 on average, 6 in the first session and 7 in the latest.")
                .contains("Minutes a week: week of 2026-09-21 60 minutes.");
        assertNoRawCodes(text);
        assertThatThrownBy(() -> activity.invoke(CALL, period().put("key", "SPORT/../ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an endpoint that fails or answers with something unreadable is an observation, not a failure")
    void unavailableEndpoints() {
        when(workout.trainingProgress(anyString(), any(), any(), any())).thenReturn(null);
        when(analytics.goalProgress(anyString(), any(), any(), any())).thenReturn("<html>bad gateway</html>");
        when(analytics.foodProgress(anyString(), any(), any(), any())).thenReturn("[1,2]");
        when(workout.exerciseProgress(anyString(), any(), any(), any(), any())).thenReturn(null);
        when(workout.activityProgress(anyString(), any(), anyString(), any(), any())).thenReturn(null);

        for (ToolResult result : List.of(
                training.invoke(CALL, period()),
                goal.invoke(CALL, period()),
                food.invoke(CALL, period()),
                exercise.invoke(CALL, period().put("exerciseId", UUID.randomUUID().toString())),
                activity.invoke(CALL, period().put("key", "CARDIO")))) {
            assertThat(result.summary()).isEqualTo("unavailable");
            assertThat(result.observation()).contains("could not be read. Answer without it, or say so.");
        }
    }

    @Test
    @DisplayName("an empty body says nothing is recorded rather than inventing zeros")
    void emptyBody() {
        when(workout.trainingProgress(anyString(), any(), any(), any())).thenReturn("{\"hero\":{}}");

        ToolResult result = training.invoke(CALL, period());

        assertThat(result.summary()).isEqualTo("nothing recorded");
        assertThat(result.observation()).doesNotContain(": 0");
    }

    @Test
    @DisplayName("no period means the last 28 days in the caller's zone; a bad period is rejected before any read")
    void periods() {
        when(workout.trainingProgress(anyString(), any(), any(), any())).thenReturn(null);

        training.invoke(CALL, objectMapper.createObjectNode());
        // Ten in the morning UTC on 1 Oct is six in the evening in Singapore, still 1 Oct.
        verify(workout).trainingProgress(CALLER, "Asia/Singapore", LocalDate.parse("2026-09-04"), LocalDate.parse("2026-10-01"));

        for (ObjectNode bad : List.of(
                objectMapper.createObjectNode().put("from", "2026-09-01"),
                objectMapper.createObjectNode().put("from", "2026-09-28").put("to", "2026-09-01"),
                objectMapper.createObjectNode().put("from", "2025-01-01").put("to", "2026-01-07"),
                objectMapper.createObjectNode().put("from", "last week").put("to", "2026-09-01"))) {
            assertThatThrownBy(() -> goal.invoke(CALL, bad)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(analytics);
    }

    @Test
    @DisplayName("an answer that copies the tool's figures passes the answer check")
    void copiedFiguresPassTheAnswerCheck() {
        when(analytics.foodProgress(anyString(), any(), any(), any())).thenReturn(FOOD);
        String observation = food.invoke(CALL, period()).observation();

        AnswerValidator.Result result = new AnswerValidator().validate(
                "You ate 2,105.4 kcal a day on logged days, against 2,230 kcal in the period before, "
                        + "and 121 g of protein a day.",
                "How is my food going?", List.of(observation), null);

        assertThat(result.valid()).as(result.violations().toString()).isTrue();
    }

    @Test
    @DisplayName("each tool names itself as a read tool")
    void describeThemselves() {
        assertThat(List.of(training, goal, food, exercise, activity))
                .extracting(CoachTool::name)
                .containsExactly("readTrainingProgress", "readGoalProgress", "readFoodProgress",
                        "readExerciseProgress", "readActivityProgress");
        assertThat(List.of(training, goal, food, exercise, activity))
                .allMatch(tool -> tool.toolClass() == ToolClass.READ);
    }

    private static void assertNoRawCodes(String text) {
        assertThat(CoachVoice.RAW_STATUS_WORD.matcher(text).find()).as(text).isFalse();
        for (String code : List.of("TIRED", "INTERMEDIATE", "ADVANCED", "STEADY", "NOT_LOGGED", "WORKOUT_FREQUENCY",
                "VEGETABLES", "BREAKFAST", "SPORT:", "LEGS", "CHEST", "KG")) {
            assertThat(text).as(text).doesNotContain(code);
        }
    }
}
