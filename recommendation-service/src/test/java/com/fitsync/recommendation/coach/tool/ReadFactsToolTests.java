package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.client.UserProfileClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.reading.ProgressFactsBuilder;
import com.fitsync.recommendation.coach.reading.ProgressInsight;
import com.fitsync.recommendation.coach.orchestrator.AnswerValidator;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadFactsToolTests {

    private static final UUID USER = UUID.randomUUID();
    private static final CoachCallContext CALL = new CoachCallContext(USER, "caller", UUID.randomUUID());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationContextRepository repository = mock(RecommendationContextRepository.class);
    private final CoachFacts coachFacts = mock(CoachFacts.class);
    private final WorkoutCatalogueClient workoutClient = mock(WorkoutCatalogueClient.class);
    private final NutritionCatalogueClient nutritionClient = mock(NutritionCatalogueClient.class);
    private final UserCalendar calendar = mock(UserCalendar.class);
    private final UserProfileClient userClient = mock(UserProfileClient.class);
    private final ReadFactsTool tool = new ReadFactsTool(repository, coachFacts, objectMapper, workoutClient,
            nutritionClient, calendar, userClient);

    @BeforeEach
    void todayIsTheFirstOfOctoberInSingapore() {
        ZoneId singapore = ZoneId.of("Asia/Singapore");
        when(calendar.zone(USER)).thenReturn(singapore);
        when(calendar.today(singapore)).thenReturn(LocalDate.parse("2026-10-01"));
    }

    @Test
    @DisplayName("today's diary figures join nutritionToday and the seven-day vitamins get their own group")
    void addsTheDiaryFigures() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        when(nutritionClient.dailySummary(CALL.bearerToken(), LocalDate.parse("2026-10-01"), "Asia/Singapore"))
                .thenReturn(DiaryFixtures.SUMMARY);
        when(nutritionClient.nutrientsSummary(CALL.bearerToken(), LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-01"), "Asia/Singapore")).thenReturn(DiaryFixtures.NUTRIENTS);
        when(nutritionClient.nutrientsSummary(CALL.bearerToken(), LocalDate.parse("2026-09-25"),
                LocalDate.parse("2026-10-01"), "Asia/Singapore")).thenReturn(DiaryFixtures.NUTRIENTS);

        ToolResult result = tool.invoke(CALL, objectMapper.createObjectNode());
        JsonNode facts = objectMapper.readTree(result.observation());

        JsonNode today = facts.path("nutritionToday");
        assertThat(today.path("consumedCalories").asInt()).isEqualTo(1200);
        assertThat(today.path("fibre").asText()).isEqualTo("22.4 g of 30 g (75%)");
        assertThat(today.path("water").asText()).isEqualTo("1200 ml plain water and 350 ml from drinks");
        assertThat(today.path("lowestVitaminsAndMinerals")).hasSize(3);
        JsonNode week = facts.path("micronutrientsWeek");
        assertThat(week.path("lowest").get(0).asText()).startsWith("Vitamin D 12%");
        assertThat(week.path("aboveUpperLimit").get(0).asText()).contains("Vitamin A");
        assertThat(tool.promptSignature()).contains("micronutrientsWeek");
    }

    @Test
    @DisplayName("without the new fields, or when nutrition-service fails, the facts are as they were")
    void leavesTheFactsAloneWithoutTheDiary() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        when(nutritionClient.dailySummary(any(), any(), any())).thenReturn("{\"totalCalories\":1200}");
        when(nutritionClient.nutrientsSummary(any(), any(), any(), any())).thenReturn(null);

        JsonNode old = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(old.path("nutritionToday").path("consumedCalories").asInt()).isEqualTo(1200);
        assertThat(old.path("nutritionToday").has("fibre")).isFalse();
        assertThat(old.has("micronutrientsWeek")).isFalse();

        when(nutritionClient.dailySummary(any(), any(), any())).thenThrow(new IllegalStateException("down"));
        JsonNode failing = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(failing.path("training").path("workoutCount").asInt()).isEqualTo(4);
        assertThat(failing.has("micronutrientsWeek")).isFalse();
    }

    @Test
    @DisplayName("an answer quoting the new facts passes the answer check, a share worked out by the model does not")
    void theAnswerCheckAcceptsTheDiaryFacts() {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        when(nutritionClient.dailySummary(any(), any(), any())).thenReturn(DiaryFixtures.SUMMARY);
        when(nutritionClient.nutrientsSummary(any(), any(), any(), any())).thenReturn(DiaryFixtures.NUTRIENTS);
        ToolResult result = tool.invoke(CALL, objectMapper.createObjectNode());
        List<String> transcript = List.of("Result of readFacts({}):\n" + result.observation());
        AnswerValidator validator = new AnswerValidator();

        assertThat(validator.validate("You have 22.4 g of fibre, which is 75% of 30 g, and 350 ml of water came "
                + "from drinks. Vitamin D is at 12%.", "How is my diet?", transcript, null).valid()).isTrue();
        assertThat(validator.validate("You are about 74% of the way to your fibre target.", "How is my diet?",
                transcript, null).violations()).containsExactly("NUMBER_NOT_IN_RECORDS:74");
    }

    @Test
    @DisplayName("the current week so far is handed over beside the last full week")
    void addsTheCurrentWeekSoFar() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(CALL.bearerToken(), "Asia/Singapore")).thenReturn(
                "{\"weekStart\":\"2026-09-28\",\"completedSessions\":1,\"totalMinutes\":50}");

        JsonNode facts = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(facts.path("training").path("workoutCount").asInt()).isEqualTo(4);
        JsonNode week = facts.path("trainingThisWeek");
        assertThat(week.path("period").asText()).isEqualTo("CURRENT_WEEK_SO_FAR");
        assertThat(week.path("weekStart").asText()).isEqualTo("2026-09-28");
        assertThat(week.path("sessions").asInt()).isEqualTo(1);
        assertThat(week.path("totalMinutes").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("the facts are still returned when workout-service cannot be reached")
    void returnsTheFactsWithoutTheCurrentWeek() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);

        JsonNode facts = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(facts.path("training").path("workoutCount").asInt()).isEqualTo(4);
        assertThat(facts.has("trainingThisWeek")).isFalse();
    }

    @Test
    @DisplayName("a failing or unreadable answer from workout-service leaves the current week out")
    void leavesTheCurrentWeekOutWhenTheAnswerCannotBeUsed() throws Exception {
        userWithFacts(null);
        when(workoutClient.weeklyInsight(CALL.bearerToken(), "UTC")).thenReturn("not json");
        JsonNode unreadable = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        when(workoutClient.weeklyInsight(CALL.bearerToken(), "UTC")).thenThrow(new IllegalStateException("down"));
        JsonNode failing = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(unreadable.has("trainingThisWeek")).isFalse();
        assertThat(failing.has("trainingThisWeek")).isFalse();
    }

    @Test
    @DisplayName("today is handed over with its kind, its source and the next training day")
    void addsToday() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(CALL.bearerToken(), "Asia/Singapore")).thenReturn(
                "{\"completedSessions\":1,\"totalMinutes\":50,\"today\":{\"date\":\"2026-09-30\","
                        + "\"kind\":\"CARDIO\",\"label\":\"Cardio\",\"source\":\"PLAN\","
                        + "\"title\":\"Treadmill run\",\"estimatedMinutes\":35,\"moves\":null},"
                        + "\"nextTraining\":{\"date\":\"2026-10-02\",\"kind\":\"SPORT\","
                        + "\"label\":\"Badminton\",\"title\":null,\"estimatedMinutes\":60}}");

        JsonNode facts = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        JsonNode today = facts.path("today");
        assertThat(today.path("kind").asText()).isEqualTo("CARDIO");
        assertThat(today.path("label").asText()).isEqualTo("Cardio");
        assertThat(today.path("source").asText()).isEqualTo("PLAN");
        assertThat(today.path("title").asText()).isEqualTo("Treadmill run");
        assertThat(today.path("estimatedMinutes").asInt()).isEqualTo(35);
        assertThat(today.path("next").path("date").asText()).isEqualTo("2026-10-02");
        assertThat(today.path("next").path("label").asText()).isEqualTo("Badminton");
        assertThat(today.path("next").path("day").asText()).isEqualTo("Friday 2 October");
        assertThat(facts.path("trainingThisWeek").path("sessions").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a rest day without a next training day has no title, minutes or next")
    void addsTodayWithoutNext() throws Exception {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(CALL.bearerToken(), "Asia/Singapore")).thenReturn(
                "{\"today\":{\"kind\":\"REST\",\"label\":\"Rest\",\"source\":\"HABIT\","
                        + "\"title\":null,\"estimatedMinutes\":null},\"nextTraining\":null}");

        JsonNode today = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation())
                .path("today");

        assertThat(today.path("kind").asText()).isEqualTo("REST");
        assertThat(today.path("source").asText()).isEqualTo("HABIT");
        assertThat(today.has("title")).isFalse();
        assertThat(today.has("estimatedMinutes")).isFalse();
        assertThat(today.has("next")).isFalse();
    }

    @Test
    @DisplayName("today is left out when the weekly insight has none, cannot be read, or fails")
    void leavesTodayOut() throws Exception {
        userWithFacts(null);
        when(workoutClient.weeklyInsight(CALL.bearerToken(), "UTC")).thenReturn(
                "{\"completedSessions\":1,\"totalMinutes\":50}");
        JsonNode without = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        when(workoutClient.weeklyInsight(CALL.bearerToken(), "UTC")).thenReturn("not json");
        JsonNode unreadable = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        when(workoutClient.weeklyInsight(CALL.bearerToken(), "UTC")).thenThrow(new IllegalStateException("down"));
        JsonNode failing = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation());

        assertThat(without.has("today")).isFalse();
        assertThat(without.has("trainingThisWeek")).isTrue();
        assertThat(unreadable.has("today")).isFalse();
        assertThat(failing.has("today")).isFalse();
    }

    @Test
    @DisplayName("a user without computed facts is told so, and workout-service is not asked")
    void saysThereAreNoFactsYet() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        ToolResult result = tool.invoke(CALL, objectMapper.createObjectNode());

        assertThat(result.summary()).isEqualTo("no facts yet");
    }

    private static ProgressInsight liveInsight() {
        return new ProgressInsight("Asia/Singapore", LocalDate.parse("2026-10-03"), new BigDecimal("70.4"),
                LocalDate.parse("2026-10-02"), 12, new BigDecimal("-0.41"), 28, new BigDecimal("68.0"),
                LocalDate.parse("2026-12-11"), LocalDate.parse("2026-11-25"));
    }

    @Test
    @DisplayName("goalPace is read live and states the same weight, rate, goal date and projected date as the reading")
    void goalPaceMatchesTheProgressReading() throws Exception {
        userWithFacts("Asia/Singapore");
        Map<String, Object> stale = new LinkedHashMap<>();
        stale.put("status", "BEHIND");
        stale.put("requiredRatePerWeekKg", "0.32");
        facts.put("goalPace", stale);
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        when(userClient.progressInsight(CALL.bearerToken(), "Asia/Singapore")).thenReturn(liveInsight());

        JsonNode pace = objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation())
                .path("goalPace");

        List<com.fitsync.recommendation.coach.reading.ReadingFact> reading =
                new ProgressFactsBuilder().build(liveInsight(), null);
        assertThat(pace.path("latestWeightKg").asText()).isEqualTo(reading.get(0).value());
        assertThat(pace.path("weeklyChangeKg").asText()).isEqualTo(reading.get(1).value());
        assertThat(pace.path("weeklyChangeDirection").asText()).isEqualToIgnoringCase(reading.get(1).note());
        assertThat("by " + pace.path("targetDate").asText()).isEqualTo(reading.get(2).note());
        assertThat(pace.path("projectedDateAtThisRate").asText() + " at this rate")
                .isEqualTo(reading.get(2).detail());
        assertThat(pace.path("status").asText()).isEqualTo("AHEAD");
        assertThat(pace.has("requiredRatePerWeekKg")).isFalse();
    }

    @Test
    @DisplayName("the stored goalPace stays when the live read fails or holds nothing")
    void keepsTheStoredPaceWhenTheLiveReadFails() throws Exception {
        userWithFacts("Asia/Singapore");
        Map<String, Object> stale = new LinkedHashMap<>();
        stale.put("status", "BEHIND");
        facts.put("goalPace", stale);
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        when(userClient.progressInsight(any(), any())).thenReturn(null);
        assertThat(objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation())
                .path("goalPace").path("status").asText()).isEqualTo("BEHIND");

        when(userClient.progressInsight(any(), any())).thenThrow(new IllegalStateException("down"));
        assertThat(objectMapper.readTree(tool.invoke(CALL, objectMapper.createObjectNode()).observation())
                .path("goalPace").path("status").asText()).isEqualTo("BEHIND");
    }

    @Test
    @DisplayName("the zone the client reports beats the stored one, and an unknown name falls back to it")
    void theReportedZoneWinsOverTheStoredOne() {
        userWithFacts("Asia/Singapore");
        when(workoutClient.weeklyInsight(any(), any())).thenReturn(null);
        CoachCallContext tokyo = new CoachCallContext(USER, "caller", UUID.randomUUID(), "Asia/Tokyo");
        CoachCallContext nonsense = new CoachCallContext(USER, "caller", UUID.randomUUID(), "Not/AZone");

        tool.invoke(tokyo, objectMapper.createObjectNode());
        tool.invoke(nonsense, objectMapper.createObjectNode());

        org.mockito.Mockito.verify(userClient).progressInsight(CALL.bearerToken(), "Asia/Tokyo");
        org.mockito.Mockito.verify(userClient).progressInsight(CALL.bearerToken(), "Asia/Singapore");
    }

    @Test
    @DisplayName("the tool texts state facts and carry no instruction or dash")
    void toolTextsStateFactsOnly() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        String text = tool.invoke(CALL, objectMapper.createObjectNode()).observation() + tool.promptSignature();

        assertThat(text).doesNotContainIgnoringCase("encourage").doesNotContain("—").doesNotContain("–");
    }

    private final Map<String, Object> facts = new LinkedHashMap<>();

    private void userWithFacts(String timezone) {
        RecommendationContextEntity entity = new RecommendationContextEntity();
        entity.setUserId(USER);
        entity.setTimezone(timezone);
        Map<String, Object> training = new LinkedHashMap<>();
        training.put("period", "LAST_FULL_WEEK");
        training.put("workoutCount", 4);
        training.put("totalMinutes", 170);
        facts.clear();
        facts.put("training", training);
        Map<String, Object> nutritionToday = new LinkedHashMap<>();
        nutritionToday.put("consumedCalories", 1200);
        facts.put("nutritionToday", nutritionToday);
        when(repository.findById(USER)).thenReturn(Optional.of(entity));
        when(coachFacts.assemble(eq(entity), isNull())).thenReturn(facts);
    }
}
