package com.fitsync.recommendation.coach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.coach.tool.CoachTool;
import com.fitsync.recommendation.coach.tool.ToolClass;
import com.fitsync.recommendation.coach.tool.ToolRegistry;
import com.fitsync.recommendation.coach.tool.ToolResult;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The agent loop, proven against a scripted model.
 *
 * <p>A real model is the wrong instrument here. What needs proving is that the loop calls the tool
 * it was told to, feeds the result back, stops when told to answer, survives a reply it cannot
 * parse, and refuses to run forever. Each of those is a branch, and a scripted model exercises
 * every one deterministically, which a real one cannot.
 *
 * <p>Whether {@code llama3.2:3b} produces sensible actions is a separate question, answered by the
 * day-zero spike: 100% well-formed over ninety trials.
 *
 * <p>Lives in the orchestrator package so it can reach {@code plannedTools}, which is package
 * private because the planned path is an internal optimisation rather than part of the contract.
 */
class CoachOrchestratorTests {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CoachCallContext CONTEXT = new CoachCallContext(USER, "token", null);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> calls = new ArrayList<>();

    @Test
    @DisplayName("a tool action is executed and its result fed back before the answer")
    void callsAToolThenAnswers() {
        CoachOrchestrator orchestrator = orchestrator(
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest day\"}}",
                "{\"answer\":\"You trained 4 of 5 planned days.\"}");

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(calls).containsExactly("echo");
        assertThat(outcome.answer()).isEqualTo("You trained 4 of 5 planned days.");
        assertThat(outcome.toolsUsed()).containsExactly("echo");
        assertThat(outcome.tier()).isEqualTo(CoachOrchestrator.TIER_LOCAL_MODEL);
        assertThat(outcome.modelAvailable()).isTrue();
    }

    @Test
    @DisplayName("an answer on the first reply calls no tool at all")
    void answersWithoutTools() {
        CoachOutcome outcome = orchestrator("{\"answer\":\"Rest today.\"}")
                .answer(CONTEXT, "anything at all", List.of());

        assertThat(calls).isEmpty();
        assertThat(outcome.answer()).isEqualTo("Rest today.");
    }

    @Test
    @DisplayName("an unparseable reply is corrected rather than failing the turn")
    void recoversFromAnUnparseableReply() {
        CoachOutcome outcome = orchestrator(
                "I think you should train chest today.",
                "{\"answer\":\"Train chest today.\"}")
                .answer(CONTEXT, "anything at all", List.of());

        assertThat(outcome.answer()).isEqualTo("Train chest today.");
    }

    @Test
    @DisplayName("a model that only ever calls tools is stopped, and still answers")
    void stopsAtTheToolBudget() {
        CoachOrchestrator orchestrator = orchestrator(
                repeat("{\"tool\":\"echo\",\"args\":{}}", 20));

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(calls)
                .withFailMessage("the loop must not run past its tool budget")
                .hasSizeLessThanOrEqualTo(2);
        assertThat(outcome.answer()).isNotBlank();
    }

    @Test
    @DisplayName("an unknown tool name becomes an observation, not a failed turn")
    void unknownToolIsSurvivable() {
        CoachOutcome outcome = orchestrator(
                "{\"tool\":\"teleport\",\"args\":{}}",
                "{\"answer\":\"Sorry, I checked what I could.\"}")
                .answer(CONTEXT, "anything at all", List.of());

        assertThat(outcome.answer()).isEqualTo("Sorry, I checked what I could.");
    }

    @Test
    @DisplayName("with the AI tier off there is no model bean, and that is not an error")
    void degradesWhenTheModelIsOff() {
        CoachOrchestrator orchestrator = new CoachOrchestrator(
                emptyProvider(), new CoachGuardrail(), registry(), new CoachActionParser(objectMapper), properties(), objectMapper,
                java.util.concurrent.Executors.newSingleThreadExecutor());

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(outcome.modelAvailable()).isFalse();
        assertThat(outcome.tier()).isEqualTo(CoachOrchestrator.TIER_UNAVAILABLE);
        assertThat(outcome.answer()).contains("switched off");
    }

    @Test
    @DisplayName("a model that throws degrades instead of surfacing the failure")
    void degradesWhenTheModelThrows() {
        ClaudeClient throwing = (system, user) -> {
            throw new IllegalStateException("connection refused");
        };
        CoachOrchestrator orchestrator = new CoachOrchestrator(
                providerOf(throwing), new CoachGuardrail(), registry(), new CoachActionParser(objectMapper), properties(), objectMapper,
                java.util.concurrent.Executors.newSingleThreadExecutor());

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(outcome.modelAvailable()).isFalse();
        assertThat(outcome.answer()).doesNotContain("connection refused");
    }

    @Test
    @DisplayName("a repeated call with the same arguments runs the tool once")
    void refusesToRepeatAnIdenticalCall() {
        CoachOrchestrator orchestrator = orchestrator(
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}",
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}",
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}",
                "{\"answer\":\"Here are three chest movements.\"}");

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(calls)
                .withFailMessage("the same read must not be paid for twice")
                .containsExactly("echo");
        assertThat(outcome.toolsUsed()).containsExactly("echo");
        assertThat(outcome.answer()).isEqualTo("Here are three chest movements.");
    }

    @Test
    @DisplayName("the same tool with different arguments is a different call and runs again")
    void differentArgumentsAreNotARepeat() {
        CoachOrchestrator orchestrator = orchestrator(
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}",
                "{\"tool\":\"echo\",\"args\":{\"say\":\"back\"}}",
                "{\"answer\":\"Two options.\"}");

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(calls).containsExactly("echo", "echo");
        assertThat(outcome.answer()).isEqualTo("Two options.");
    }

    @Test
    @DisplayName("when the budget runs out the model still gets a turn to answer")
    void answersAfterTheBudgetIsSpent() {
        CoachOrchestrator orchestrator = orchestrator(
                "{\"tool\":\"echo\",\"args\":{\"say\":\"one\"}}",
                "{\"tool\":\"echo\",\"args\":{\"say\":\"two\"}}",
                "{\"tool\":\"echo\",\"args\":{\"say\":\"three\"}}",
                "{\"answer\":\"That is enough to go on.\"}");

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "anything at all", List.of());

        assertThat(calls)
                .withFailMessage("the budget of two must be honoured")
                .hasSize(2);
        assertThat(outcome.answer())
                .withFailMessage("spending the budget must not cost the user their answer")
                .isEqualTo("That is enough to go on.");
    }

    // --- progress reporting, which the streaming endpoint renders -----------------------------

    @Test
    @DisplayName("a watcher sees the model being asked, the tool going out, and the tool coming back")
    void reportsEachStepOfTheTurn() {
        List<String> seen = new ArrayList<>();
        CoachOrchestrator orchestrator = orchestrator(
                "{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}",
                "{\"answer\":\"Three movements.\"}");

        orchestrator.answer(CONTEXT, "anything at all", List.of(),
                event -> seen.add(event.kind() + (event.tool() == null ? "" : ":" + event.tool())));

        assertThat(seen).containsExactly(
                "THINKING",
                "TOOL_STARTED:echo",
                "TOOL_FINISHED:echo",
                "THINKING");
    }

    @Test
    @DisplayName("a planned read is reported as it is dispatched and again as it lands")
    void reportsPlannedReads() {
        List<String> seen = new ArrayList<>();

        orchestrator("{\"answer\":\"Rest today.\"}")
                .answer(CONTEXT, "should I train today?", List.of(),
                        event -> seen.add(String.valueOf(event.kind())));

        // readFacts and readRecentTraining are both planned for a training question, so two go out
        // and two come back before the model is asked anything. Deliberately not asserting that both
        // starts precede both finishes: how they interleave depends on how fast each read is, and
        // reporting a result the moment it lands is the point of the planned path.
        assertThat(seen).filteredOn("TOOL_STARTED"::equals).hasSize(2);
        assertThat(seen).filteredOn("TOOL_FINISHED"::equals).hasSize(2);
        assertThat(seen.indexOf("THINKING"))
                .withFailMessage("the model must not be asked until the planned reads are in")
                .isEqualTo(4);
        assertThat(seen).endsWith("THINKING");
    }

    @Test
    @DisplayName("a listener that throws does not take the turn down with it")
    void survivesAListenerThatThrows() {
        CoachOutcome outcome = orchestrator("{\"answer\":\"Rest today.\"}")
                .answer(CONTEXT, "anything at all", List.of(), event -> {
                    throw new IllegalStateException("client disconnected");
                });

        assertThat(outcome.answer()).isEqualTo("Rest today.");
    }

    @Test
    @DisplayName("a finished tool reports its summary and how long it took")
    void reportsToolSummaryAndLatency() {
        List<CoachProgressEvent> seen = new ArrayList<>();

        orchestrator("{\"tool\":\"echo\",\"args\":{\"say\":\"chest\"}}", "{\"answer\":\"done\"}")
                .answer(CONTEXT, "anything at all", List.of(), seen::add);

        CoachProgressEvent finished = seen.stream()
                .filter(event -> event.kind() == CoachProgressEvent.Kind.TOOL_FINISHED)
                .findFirst()
                .orElseThrow();
        assertThat(finished.summary()).isEqualTo("echoed");
        assertThat(finished.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    // --- guardrails, which answer before any model is asked ------------------------------------

    @Test
    @DisplayName("a plan to eat below the calorie floor is refused without consulting the model")
    void refusesBelowTheCalorieFloorBeforeTheModel() {
        List<String> prompts = new ArrayList<>();
        ClaudeClient scripted = (system, user) -> {
            prompts.add(user);
            return "{\"answer\":\"Sure, 800 kcal is fine.\"}";
        };
        CoachOrchestrator orchestrator = new CoachOrchestrator(
                providerOf(scripted), new CoachGuardrail(), registry(), new CoachActionParser(objectMapper),
                properties(), objectMapper, java.util.concurrent.Executors.newSingleThreadExecutor());

        CoachOutcome outcome = orchestrator.answer(CONTEXT, "Should I cut to 800 kcal a day?", List.of());

        assertThat(prompts).withFailMessage("the model must never see a question the guardrail refused").isEmpty();
        assertThat(calls).isEmpty();
        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.guardrailVerdict()).isEqualTo("REFUSED_CALORIE_FLOOR");
        assertThat(outcome.tier()).isEqualTo(CoachOrchestrator.TIER_RULE);
        assertThat(outcome.answer()).contains("1200 kcal");
    }

    @Test
    @DisplayName("an ordinary question is recorded as passed")
    void recordsAPassedVerdict() {
        CoachOutcome outcome = orchestrator("{\"answer\":\"Rest today.\"}")
                .answer(CONTEXT, "Should I train today?", List.of());

        assertThat(outcome.guardrailVerdict()).isEqualTo("PASSED");
        assertThat(outcome.refused()).isFalse();
    }

    // --- the planned path, which came out of the day-zero latency measurement -----------------

    @Test
    @DisplayName("a training question fetches facts and history up front rather than one at a time")
    void plansForTrainingQuestions() {
        CoachOrchestrator orchestrator = orchestrator("{\"answer\":\"done\"}");

        assertThat(orchestrator.plannedTools("Should I train today?"))
                .containsExactly("readFacts", "readRecentTraining");
    }

    @Test
    @DisplayName("a nutrition question plans only the facts it needs")
    void plansForNutritionQuestions() {
        assertThat(orchestrator("{\"answer\":\"done\"}").plannedTools("What should I eat tonight?"))
                .containsExactly("readFacts");
    }

    @Test
    @DisplayName("an unrelated question plans nothing and falls through to the loop")
    void plansNothingForUnrelatedQuestions() {
        assertThat(orchestrator("{\"answer\":\"done\"}").plannedTools("What is the capital of France?"))
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------------

    private CoachOrchestrator orchestrator(String... scriptedReplies) {
        return orchestrator(List.of(scriptedReplies));
    }

    private CoachOrchestrator orchestrator(List<String> scriptedReplies) {
        Deque<String> script = new ArrayDeque<>(scriptedReplies);
        ClaudeClient scripted = (system, user) ->
                script.isEmpty() ? "{\"answer\":\"script exhausted\"}" : script.poll();
        return new CoachOrchestrator(
                providerOf(scripted), new CoachGuardrail(), registry(), new CoachActionParser(objectMapper), properties(), objectMapper,
                java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    /** A registry holding one tool that records that it ran. */
    private ToolRegistry registry() {
        CoachTool echo = new CoachTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public ToolClass toolClass() {
                return ToolClass.READ;
            }

            @Override
            public String promptSignature() {
                return "echo(say?) returns what it was given";
            }

            @Override
            public ToolResult invoke(CoachCallContext context, JsonNode args) {
                calls.add("echo");
                return ToolResult.of("echoed", args.path("say").asText("nothing"));
            }
        };
        // The thread service only records; a turn-less context skips it, so a mock suffices.
        return new ToolRegistry(List.of(echo), Mockito.mock(CoachThreadService.class), objectMapper);
    }

    /** Two tool calls, so the budget is reachable inside a test without a long script. */
    private CoachProperties properties() {
        return new CoachProperties(2, null, null, null, null, null);
    }

    private static List<String> repeat(String value, int times) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            values.add(value);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ClaudeClient> providerOf(ClaudeClient client) {
        ObjectProvider<ClaudeClient> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ClaudeClient> emptyProvider() {
        ObjectProvider<ClaudeClient> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
