package com.fitsync.recommendation.coach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.dto.CoachCardContext;
import com.fitsync.recommendation.coach.dto.CoachCardKind;
import com.fitsync.recommendation.coach.dto.CoachSurface;
import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.coach.tool.CoachTool;
import com.fitsync.recommendation.coach.tool.ToolClass;
import com.fitsync.recommendation.coach.tool.ToolRegistry;
import com.fitsync.recommendation.coach.tool.ToolResult;
import com.fitsync.recommendation.coach.tool.UserCalendar;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A question asked on a Progress card: one line names the card and its period, and the card's read
 * runs first with that period. Without a card neither happens.
 */
class CoachOrchestratorCardTests {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CoachCallContext CONTEXT = new CoachCallContext(USER, "caller", null);
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-28");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> calls = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();

    @Test
    @DisplayName("a training card adds one line naming the card and period, and reads training progress first")
    void aTrainingCardNamesItselfAndIsReadFirst() {
        CoachCardContext card = new CoachCardContext(CoachCardKind.TRAINING, null, FROM, TO);

        orchestrator().answer(CONTEXT, "How is my training going?", List.of(),
                new TurnContext(CoachSurface.PROGRESS, null, null, card));

        assertThat(calls).first().isEqualTo("readTrainingProgress {\"from\":\"2026-09-01\",\"to\":\"2026-09-28\"}");
        String prompt = prompts.get(0);
        String line = "The user is asking about their Training card on the Progress page, for 2026-09-01 to 2026-09-28.";
        assertThat(prompt).containsOnlyOnce(line);
        assertThat(prompt.indexOf(line)).isLessThan(prompt.indexOf("Result of readTrainingProgress("));
        assertThat(prompt).contains("Result of readTrainingProgress({\"from\":\"2026-09-01\",\"to\":\"2026-09-28\"})");
    }

    @Test
    @DisplayName("without a card there is no card line and no progress read")
    void noCardNoLine() {
        orchestrator().answer(CONTEXT, "How is my training going?", List.of(),
                new TurnContext(CoachSurface.PROGRESS, null, null, null));

        assertThat(prompts.get(0)).doesNotContain("asking about their").doesNotContain("readTrainingProgress");
        assertThat(calls).noneMatch(call -> call.startsWith("readTrainingProgress"));
    }

    @Test
    @DisplayName("each kind maps to its read tool, and the id becomes the exercise id or the activity key")
    void eachKindMapsToItsTool() {
        CoachOrchestrator orchestrator = orchestrator();
        String exerciseId = "6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb";

        assertThat(orchestrator.cardRead(new CoachCardContext(CoachCardKind.GOAL, null, FROM, TO)).tool())
                .isEqualTo("readGoalProgress");
        assertThat(orchestrator.cardRead(new CoachCardContext(CoachCardKind.FOOD, null, FROM, TO)).tool())
                .isEqualTo("readFoodProgress");

        CoachOrchestrator.CardRead exercise = orchestrator.cardRead(
                new CoachCardContext(CoachCardKind.EXERCISE, exerciseId, FROM, TO));
        assertThat(exercise.tool()).isEqualTo("readExerciseProgress");
        assertThat(exercise.args().path("exerciseId").asText()).isEqualTo(exerciseId);

        CoachOrchestrator.CardRead sport = orchestrator.cardRead(
                new CoachCardContext(CoachCardKind.SPORT, "SPORT:TABLE_TENNIS", FROM, TO));
        assertThat(sport.tool()).isEqualTo("readActivityProgress");
        assertThat(sport.args().path("key").asText()).isEqualTo("SPORT:TABLE_TENNIS");
        assertThat(CoachOrchestrator.cardLine(new CoachCardContext(CoachCardKind.SPORT, "SPORT:TABLE_TENNIS", FROM, TO)))
                .contains("their table tennis activity");

        CoachOrchestrator.CardRead strength = orchestrator.cardRead(
                new CoachCardContext(CoachCardKind.STRENGTH, null, FROM, TO));
        assertThat(strength.args().path("key").asText()).isEqualTo("STRENGTH");
        assertThat(strength.args().path("from").asText()).isEqualTo("2026-09-01");
        assertThat(strength.args().path("to").asText()).isEqualTo("2026-09-28");
    }

    private CoachOrchestrator orchestrator() {
        ClaudeClient scripted = (system, user) -> {
            prompts.add(user);
            return "{\"answer\":\"done\"}";
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<ClaudeClient> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(scripted);
        com.fitsync.recommendation.repository.RecommendationContextRepository repository =
                Mockito.mock(com.fitsync.recommendation.repository.RecommendationContextRepository.class);
        UserCalendar calendar = new UserCalendar(repository, java.time.Clock.fixed(
                java.time.Instant.parse("2026-10-01T10:00:00Z"), java.time.ZoneOffset.UTC));
        List<CoachTool> tools = new ArrayList<>();
        for (String name : List.of("readFacts", "readRecentTraining", "readTrainingProgress", "readGoalProgress",
                "readFoodProgress", "readExerciseProgress", "readActivityProgress")) {
            tools.add(recording(name));
        }
        ToolRegistry registry = new ToolRegistry(tools, Mockito.mock(CoachThreadService.class), objectMapper);
        return new CoachOrchestrator(provider, new CoachGuardrail(), registry, new CoachActionParser(objectMapper),
                new CoachProperties(2, null, null, null, false), objectMapper,
                java.util.concurrent.Executors.newSingleThreadExecutor(), new AnswerValidator(), calendar);
    }

    private CoachTool recording(String name) {
        return new CoachTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ToolClass toolClass() {
                return ToolClass.READ;
            }

            @Override
            public String promptSignature() {
                return name + "()";
            }

            @Override
            public ToolResult invoke(CoachCallContext context, JsonNode args) {
                synchronized (calls) {
                    calls.add(name + " " + args);
                }
                return ToolResult.of("read", "Sessions: 12.");
            }
        };
    }
}
