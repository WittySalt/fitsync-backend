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
