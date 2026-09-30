package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadFactsToolTests {

    private static final UUID USER = UUID.randomUUID();
    private static final CoachCallContext CALL = new CoachCallContext(USER, "caller", UUID.randomUUID());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationContextRepository repository = mock(RecommendationContextRepository.class);
    private final CoachFacts coachFacts = mock(CoachFacts.class);
    private final WorkoutCatalogueClient workoutClient = mock(WorkoutCatalogueClient.class);
    private final ReadFactsTool tool = new ReadFactsTool(repository, coachFacts, objectMapper, workoutClient);

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

    private void userWithFacts(String timezone) {
        RecommendationContextEntity entity = new RecommendationContextEntity();
        entity.setUserId(USER);
        entity.setTimezone(timezone);
        Map<String, Object> training = new LinkedHashMap<>();
        training.put("period", "LAST_FULL_WEEK");
        training.put("workoutCount", 4);
        training.put("totalMinutes", 170);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("training", training);
        when(repository.findById(USER)).thenReturn(Optional.of(entity));
        when(coachFacts.assemble(eq(entity), isNull())).thenReturn(facts);
    }
}
