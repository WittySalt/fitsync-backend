package com.fitsync.recommendation.coach.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.tool.ToolRegistry;
import com.fitsync.recommendation.coach.tool.ToolResult;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The agent loop.
 *
 * <p>It is ours rather than the provider SDK's for two reasons. The AI tier switches between the
 * Claude API and a local {@code llama3.2:3b}, and a 3-billion-parameter model's native tool-calling
 * is not dependable enough to build on; asking for one JSON action at a time behaves identically on
 * both. And an SDK-owned loop is opaque, whereas this one persists every step, which is what makes
 * the trace a user can expand possible at all.
 *
 * <p>There are two paths through it. Known intents take a <b>planned</b> path: the tools they need
 * are fetched up front, concurrently, and the model is asked once for an answer. Everything else
 * takes the general loop, one action at a time. The split came out of the day-zero spike rather
 * than from taste — a serial loop of three reads plus a draft measured about ten seconds on the
 * local model, which is too long to sit in front of, and those three reads do not depend on each
 * other.
 *
 * <p>When the AI tier is off there is no {@link ClaudeClient} bean at all. That is not an error
 * state: the rules and templates answer without a model, and this class says so plainly rather
 * than failing.
 */
@Service
public class CoachOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(CoachOrchestrator.class);

    /** Tier 2 is the local model. Recorded on the turn so the trace can show what answered. */
    public static final int TIER_LOCAL_MODEL = 2;
    public static final int TIER_UNAVAILABLE = -1;

    /**
     * Loop steps beyond the tool budget, reserved for corrections.
     *
     * <p>Three covers the ones that actually occur: a repeated call, a reply that did not parse, and
     * the final prompt to answer once the budget is gone.
     */
    private static final int STEPS_FOR_CORRECTIONS = 3;

    private static final String SYSTEM_PROMPT_HEAD = """
            You are the FitSync coach. You help one user with their training and nutrition.

            Every number you state must come from a tool result. You never calculate, estimate or
            invent a figure. If a tool has not given you a number, you do not have it.

            You may call tools to gather what you need. Reply with ONE JSON object and nothing else:
              {"tool":"<name>","args":{...}}   to call a tool
              {"answer":"<text>"}              when you can answer

            Tools:
            """;

    private static final String SYSTEM_PROMPT_TAIL = """

            Call a tool only when you do not already have what it returns. A result shown above is
            yours to use; asking for it again tells you nothing new.

            When you answer: two or three sentences, plain and specific, addressed to the user.
            If the facts do not cover the question, say what you can see and that the rest is
            outside what you know. Never guess.
            """;

    private final ObjectProvider<ClaudeClient> claudeClient;
    private final ToolRegistry toolRegistry;
    private final CoachActionParser actionParser;
    private final CoachProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;

    public CoachOrchestrator(ObjectProvider<ClaudeClient> claudeClient,
                             ToolRegistry toolRegistry,
                             CoachActionParser actionParser,
                             CoachProperties properties,
                             ObjectMapper objectMapper,
                             @Qualifier("coachToolExecutor") ExecutorService toolExecutor) {
        this.claudeClient = claudeClient;
        this.toolRegistry = toolRegistry;
        this.actionParser = actionParser;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Answers one question.
     *
     * @param history prior turns, oldest first, already trimmed to the context window
     */
    public CoachOutcome answer(CoachCallContext context, String question, List<String> history) {
        ClaudeClient client = claudeClient.getIfAvailable();
        if (client == null) {
            return CoachOutcome.unavailable(
                    "The coach's language model is switched off, so I can only show the insights "
                            + "computed from your data. Those are on your dashboard.");
        }

        long startedAt = System.nanoTime();
        String system = SYSTEM_PROMPT_HEAD + toolRegistry.promptBlock() + SYSTEM_PROMPT_TAIL;
        List<String> transcript = new ArrayList<>(history);
        List<String> toolsUsed = new ArrayList<>();
        Set<String> alreadyCalled = new LinkedHashSet<>();

        for (Map.Entry<String, ToolResult> planned : runPlannedTools(context, question).entrySet()) {
            transcript.add(observationOf(planned.getKey(), "{}", planned.getValue()));
            toolsUsed.add(planned.getKey());
            alreadyCalled.add(signature(planned.getKey(), "{}"));
        }

        int toolCalls = 0;
        for (int step = 0; step < maxSteps(); step++) {
            String reply;
            try {
                reply = client.complete(system, userPrompt(question, transcript));
            } catch (RuntimeException ex) {
                LOG.warn("Model call failed on step {}", step, ex); // step is ours, not the model's
                return CoachOutcome.unavailable(
                        "I could not reach the coach model just now. Your computed insights are "
                                + "still on your dashboard.");
            }

            CoachAction action = actionParser.parse(reply);
            switch (action.kind()) {
                case ANSWER -> {
                    return CoachOutcome.answered(action.answer(), toolsUsed, elapsedMs(startedAt));
                }
                case TOOL -> {
                    String args = action.args() == null ? "{}" : action.args().toString();
                    if (!alreadyCalled.add(signature(action.tool(), args))) {
                        // A 3B model that cannot see its own progress will happily ask for the same
                        // read over and over. Refusing costs nothing, since the result it wants is
                        // already sitting in the transcript, and it keeps a stuck turn cheap.
                        transcript.add("You already called " + action.tool() + " with those arguments "
                                + "and its result is above. Do not call it again. Answer now.");
                        continue;
                    }
                    if (toolCalls >= properties.maxToolCalls()) {
                        transcript.add("You have used all available tool calls. Answer now with what you have.");
                        continue;
                    }
                    ToolResult result = toolRegistry.invoke(context, action.tool(), action.args());
                    transcript.add(observationOf(action.tool(), args, result));
                    toolsUsed.add(action.tool());
                    toolCalls++;
                }
                case UNPARSEABLE -> transcript.add(
                        "That reply was not a single JSON object. Reply with exactly one of "
                                + "{\"tool\":...} or {\"answer\":...}.");
                default -> throw new IllegalStateException("Unhandled action kind");
            }
        }

        return CoachOutcome.answered(
                "I gathered what I could but could not put an answer together. Try asking more narrowly.",
                toolsUsed, elapsedMs(startedAt));
    }

    /**
     * How many times the loop may go round.
     *
     * <p>More than the tool budget, because the corrections the loop sends back — a repeated call, an
     * unparseable reply, a budget that has run out — each need a further turn of the model to have
     * any effect, and none of them spends a tool call. Bounding steps rather than only tool calls is
     * what stops a model that never answers from running indefinitely.
     */
    private int maxSteps() {
        return properties.maxToolCalls() + STEPS_FOR_CORRECTIONS;
    }

    /** Labels a result with the call that produced it, so repeats are visible to the model. */
    private static String observationOf(String tool, String args, ToolResult result) {
        return "Result of " + tool + "(" + args + "):" + System.lineSeparator() + result.observation();
    }

    private static String signature(String tool, String args) {
        return tool + "|" + args;
    }

    /**
     * Runs the planned reads at once on the coach's own pool.
     *
     * <p>They do not depend on each other, so paying for them one round trip at a time was simply
     * waste: the day-zero measurement put a serial gather at about ten seconds on the local model.
     * Results are collected back in plan order so the prompt the model sees is deterministic, which
     * matters for reproducing an answer later.
     */
    private Map<String, ToolResult> runPlannedTools(CoachCallContext context, String question) {
        Set<String> planned = plannedTools(question);
        if (planned.isEmpty()) {
            return Map.of();
        }

        Map<String, CompletableFuture<ToolResult>> inFlight = new LinkedHashMap<>();
        for (String tool : planned) {
            inFlight.put(tool, CompletableFuture.supplyAsync(
                    () -> toolRegistry.invoke(context, tool, objectMapper.createObjectNode()),
                    toolExecutor));
        }

        Map<String, ToolResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, CompletableFuture<ToolResult>> entry : inFlight.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().join());
            } catch (RuntimeException ex) {
                LOG.warn("Planned tool {} failed", safeForLog(entry.getKey()), ex);
                results.put(entry.getKey(), ToolResult.of("tool failed",
                        "The " + entry.getKey() + " tool failed. Answer without it, or say so."));
            }
        }
        return results;
    }

    /**
     * Tools a question is known to need, fetched up front so the loop does not pay for them one
     * round trip at a time.
     *
     * <p>Kept deliberately small and literal. A misjudged plan costs one wasted read, and the loop
     * still corrects it, so this does not need to be clever — and a keyword match is something an
     * assessor can read, which a learned classifier is not.
     */
    Set<String> plannedTools(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        Set<String> planned = new LinkedHashSet<>();

        boolean aboutTraining = containsAny(lower, "train", "workout", "session", "exercise", "gym", "lift");
        boolean aboutProgress = containsAny(lower, "week", "pace", "goal", "streak", "progress", "on track");
        boolean aboutFood = containsAny(lower, "eat", "meal", "food", "protein", "calorie", "dinner", "lunch");

        if (aboutTraining || aboutProgress || aboutFood) {
            planned.add("readFacts");
        }
        if (aboutTraining) {
            planned.add("readRecentTraining");
        }
        return planned;
    }

    private String userPrompt(String question, List<String> transcript) {
        StringBuilder prompt = new StringBuilder();
        for (String entry : transcript) {
            prompt.append(entry).append("\n\n");
        }
        prompt.append("User question: ").append(question);
        return prompt.toString();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Tool names come from the plan or the model, so they are untrusted on a log line. */
    private static String safeForLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }

    private static int elapsedMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }
}
