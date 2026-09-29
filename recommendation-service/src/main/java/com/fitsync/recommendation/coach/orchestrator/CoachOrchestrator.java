package com.fitsync.recommendation.coach.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.dto.CoachSurface;
import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
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

    /** Tier 0 is a rule: a guardrail that answered so a model did not have to. */
    public static final int TIER_RULE = 0;
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

    /**
     * The name a pinned reading's observation is recorded under in the trace. Deliberately not a
     * registered {@link com.fitsync.recommendation.coach.tool.CoachTool}: it never appears in the
     * tool catalogue the model is shown, so the model cannot ask for it — it is placed in the
     * transcript directly, the same way a planned tool's result is, because the security rule behind
     * this feature is that a client can only ever point at a reading the server already computed, not
     * hand the server facts to relay unchecked.
     */
    static final String PINNED_READING_TOOL_NAME = "readPinnedReading";

    /** A citation marker such as {@code [2]}, with any leading space, stripped from reading text. */
    private static final java.util.regex.Pattern CITATION_MARKER = java.util.regex.Pattern.compile("\\s?\\[\\d+\\]");

    /** How much of any one fact part (label, value, unit, note, detail) reaches the transcript. */
    private static final int MAX_FACT_PART_LENGTH = 120;

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

            How you speak: you describe what the records show and leave every decision to the
            user. When the user asks whether to do something, say what the records show on each
            side and that the choice is theirs. No instruction, no "you should", no "consider",
            no "try", no "keep it up". No exclamation mark and no dash used as punctuation.
            Say exactly what a number measures, in the words its label uses. A figure for one
            session is not a figure for the week.
            Write nothing in square brackets, except the number of a fact from a pinned reading.
            """;

    private final ObjectProvider<ClaudeClient> claudeClient;
    private final CoachGuardrail guardrail;
    private final ToolRegistry toolRegistry;
    private final CoachActionParser actionParser;
    private final CoachProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;
    private final AnswerValidator answerValidator;

    public CoachOrchestrator(ObjectProvider<ClaudeClient> claudeClient,
                             CoachGuardrail guardrail,
                             ToolRegistry toolRegistry,
                             CoachActionParser actionParser,
                             CoachProperties properties,
                             ObjectMapper objectMapper,
                             @Qualifier("coachToolExecutor") ExecutorService toolExecutor,
                             AnswerValidator answerValidator) {
        this.claudeClient = claudeClient;
        this.guardrail = guardrail;
        this.toolRegistry = toolRegistry;
        this.actionParser = actionParser;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
        this.answerValidator = answerValidator;
    }

    /**
     * Answers one question.
     *
     * @param history prior turns, oldest first, already trimmed to the context window
     */
    public CoachOutcome answer(CoachCallContext context, String question, List<String> history) {
        return answer(context, question, history, null, CoachProgressListener.NONE);
    }

    /**
     * Answers one question, reporting each step to a listener as it happens.
     *
     * @param listener called from the tool pool as well as this thread, and never allowed to fail
     *                 the turn
     */
    public CoachOutcome answer(CoachCallContext context,
                               String question,
                               List<String> history,
                               CoachProgressListener listener) {
        return answer(context, question, history, null, listener);
    }

    /**
     * Answers one question with the page and reading the user was looking at when they asked it.
     *
     * @param turnContext the surface and any pinned reading, already resolved by the caller, or null
     *                    when the request carried no context at all
     */
    public CoachOutcome answer(CoachCallContext context,
                               String question,
                               List<String> history,
                               TurnContext turnContext) {
        return answer(context, question, history, turnContext, CoachProgressListener.NONE);
    }

    /**
     * Answers one question, with page context and reporting each step to a listener as it happens.
     *
     * @param turnContext the surface and any pinned reading, already resolved by the caller, or null
     *                    when the request carried no context at all
     * @param listener    called from the tool pool as well as this thread, and never allowed to fail
     *                    the turn
     */
    public CoachOutcome answer(CoachCallContext context,
                               String question,
                               List<String> history,
                               TurnContext turnContext,
                               CoachProgressListener listener) {
        long startedAt = System.nanoTime();

        // Before any model, and before any tool: a guardrail is a rule, and a rule that runs first
        // cannot be talked out of it. A refusal is an answer in its own right, recorded as such.
        Optional<CoachGuardrail.Refusal> refusal = guardrail.check(question);
        if (refusal.isPresent()) {
            return CoachOutcome.refused(refusal.get().rule(), refusal.get().message(), elapsedMs(startedAt));
        }

        ClaudeClient client = claudeClient.getIfAvailable();
        if (client == null) {
            return CoachOutcome.unavailable(
                    "The coach's language model is switched off, so I can only show the insights "
                            + "computed from your data. Those are on your dashboard.");
        }

        String system = SYSTEM_PROMPT_HEAD + toolRegistry.promptBlock() + SYSTEM_PROMPT_TAIL;
        List<String> transcript = new ArrayList<>(history);
        List<String> toolsUsed = new ArrayList<>();
        Set<String> alreadyCalled = new LinkedHashSet<>();

        // The page and the pinned reading go in ahead of anything the loop itself gathers, so a
        // model that answers on its very first reply still opens from the same context a human
        // helper would already have.
        if (turnContext != null && turnContext.surface() != null) {
            transcript.add(surfaceLine(turnContext.surface()));
        }
        if (turnContext != null && turnContext.pinnedReading() != null) {
            transcript.add(pinnedReadingObservation(turnContext.pinnedReading()));
            toolsUsed.add(PINNED_READING_TOOL_NAME);
        }

        for (Map.Entry<String, ToolResult> planned : runPlannedTools(context, question, listener).entrySet()) {
            transcript.add(observationOf(planned.getKey(), "{}", planned.getValue()));
            toolsUsed.add(planned.getKey());
            alreadyCalled.add(signature(planned.getKey(), "{}"));
        }

        int toolCalls = 0;
        for (int step = 0; step < maxSteps(); step++) {
            String reply;
            try {
                emit(listener, CoachProgressEvent.thinking());
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
                    return finalizeAnswer(client, system, question, transcript, turnContext,
                            toolsUsed, startedAt, action.answer(), listener);
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
                    emit(listener, CoachProgressEvent.started(action.tool()));
                    long toolStartedAt = System.nanoTime();
                    ToolResult result = toolRegistry.invoke(context, action.tool(), action.args());
                    emit(listener, CoachProgressEvent.finished(
                            action.tool(), result.summary(), elapsedMs(toolStartedAt)));
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

        // Out of steps. The evaluation harness showed this ending accounted for most grounding
        // misses: the facts were in the transcript and the model kept asking for tools instead of
        // using them. So the last call removes the tools altogether. Shown no tool catalogue and
        // told to answer, a small model answers from what it has.
        try {
            CoachAction last = actionParser.parse(client.complete(
                    ANSWER_ONLY_PROMPT, userPrompt(question, transcript)));
            if (last.kind() == CoachAction.Kind.ANSWER) {
                return finalizeAnswer(client, ANSWER_ONLY_PROMPT, question, transcript, turnContext,
                        toolsUsed, startedAt, last.answer(), listener);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Model call failed on the final answer-only attempt", ex);
        }
        return CoachOutcome.answered(
                "I gathered what I could but could not put an answer together. Try asking more narrowly.",
                toolsUsed, elapsedMs(startedAt));
    }

    /** The prompt for the last resort: no tools exist, only an answer is possible. */
    private static final String ANSWER_ONLY_PROMPT = """
            You are the FitSync coach. You help one user with their training and nutrition.

            You have no tools. The results of every tool you already used are in the conversation
            below. Answer the user's question now from those results, in two or three plain
            sentences. Every number must come from a result shown below. If the results do not
            cover the question, say what you can see and that the rest is outside what you know.

            How you speak: you describe what the records show and leave every decision to the
            user. When the user asks whether to do something, say what the records show on each
            side and that the choice is theirs. No instruction, no "you should", no "consider",
            no "try", no "keep it up". No exclamation mark and no dash used as punctuation.
            Say exactly what a number measures, in the words its label uses. A figure for one
            session is not a figure for the week.
            Write nothing in square brackets, except the number of a fact from a pinned reading.

            Reply with ONE JSON object and nothing else: {"answer":"<text>"}
            """;

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
    private Map<String, ToolResult> runPlannedTools(CoachCallContext context,
                                                    String question,
                                                    CoachProgressListener listener) {
        Set<String> planned = plannedTools(question);
        if (planned.isEmpty()) {
            return Map.of();
        }

        Map<String, CompletableFuture<ToolResult>> inFlight = new LinkedHashMap<>();
        for (String tool : planned) {
            emit(listener, CoachProgressEvent.started(tool));
            long startedAt = System.nanoTime();
            inFlight.put(tool, CompletableFuture
                    .supplyAsync(() -> toolRegistry.invoke(context, tool, objectMapper.createObjectNode()),
                            toolExecutor)
                    // Reported as each one lands rather than in plan order, so a watching user sees
                    // the concurrency that is actually happening. The prompt still assembles in plan
                    // order below, because an answer has to be reproducible.
                    .whenComplete((result, failure) -> emit(listener, CoachProgressEvent.finished(
                            tool,
                            failure == null ? result.summary() : "tool failed",
                            elapsedMs(startedAt)))));
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
        // "day" and "today" were missing at first, and the harness's very first question, "how is
        // my day going?", got "outside what I know" because nothing had been read.
        boolean aboutProgress = containsAny(lower, "week", "pace", "goal", "streak", "progress", "on track",
                "day", "today", "how am i", "where do i stand", "summar");
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

    /** Anything in square brackets, with the space before it. */
    private static final java.util.regex.Pattern BRACKETED = java.util.regex.Pattern.compile("\\s?\\[([^\\[\\]]{0,200})]");

    /**
     * Removes what the model put in square brackets, except a citation of a pinned fact.
     *
     * <p>A model that has been shown citations tends to invent its own, such as a field name from a
     * tool result. Those mean nothing to a reader, and a citation of a fact that does not exist
     * would point nowhere. This is a cleanup of form. It does not check that the answer is true.
     */
    static String tidy(String answer, TurnContext turnContext) {
        if (answer == null || answer.indexOf('[') < 0) {
            return answer;
        }
        int factCount = turnContext == null || turnContext.pinnedReading() == null
                ? 0
                : turnContext.pinnedReading().facts().size();
        java.util.regex.Matcher matcher = BRACKETED.matcher(answer);
        StringBuilder tidied = new StringBuilder();
        while (matcher.find()) {
            String inside = matcher.group(1).strip();
            boolean citation = inside.matches("\\d{1,2}") && Integer.parseInt(inside) >= 1
                    && Integer.parseInt(inside) <= factCount;
            matcher.appendReplacement(tidied, citation
                    ? java.util.regex.Matcher.quoteReplacement(" [" + inside + "]")
                    : "");
        }
        matcher.appendTail(tidied);
        return tidied.toString().replaceAll(" {2,}", " ").strip();
    }

    /**
     * Checks a model's answer before the turn returns it, and gives the model exactly one chance to
     * fix a failing one.
     *
     * <p>Every answer the loop is about to return goes through here, whether it came from the main
     * loop or from the answer-only attempt made once the tool budget is spent, so the check cannot be
     * skipped by a model that stalls on tools until the very end. When {@link AnswerValidator} passes
     * the answer, or when {@code fitsync.coach.answer-validation-enabled} is off, this behaves exactly
     * as returning the tidied answer always did. When it fails, one correction is appended to the
     * transcript and the model is asked again with the same system prompt this attempt used; if that
     * second answer also fails — or the model does not answer at all, or the check itself throws —
     * the turn ends with a fixed sentence rather than anything the model wrote, so a citation attached
     * to the wrong fact or a per-session figure called weekly is never what the user is shown.
     */
    private CoachOutcome finalizeAnswer(ClaudeClient client,
                                        String system,
                                        String question,
                                        List<String> transcript,
                                        TurnContext turnContext,
                                        List<String> toolsUsed,
                                        long startedAt,
                                        String rawAnswer,
                                        CoachProgressListener listener) {
        String candidate = tidy(rawAnswer, turnContext);
        if (!properties.answerValidationEnabled()) {
            return CoachOutcome.answered(candidate, toolsUsed, elapsedMs(startedAt));
        }

        AnswerValidator.Result firstResult = validateSafely(candidate, question, transcript, turnContext);
        if (firstResult.valid()) {
            return CoachOutcome.answered(candidate, toolsUsed, elapsedMs(startedAt));
        }

        List<String> correctedTranscript = new ArrayList<>(transcript);
        correctedTranscript.add(explainAnswerViolations(firstResult.violations()));

        String secondReply;
        try {
            emit(listener, CoachProgressEvent.thinking());
            secondReply = client.complete(system, userPrompt(question, correctedTranscript));
        } catch (RuntimeException ex) {
            LOG.warn("Model call failed on the answer-validation correction attempt", ex);
            return CoachOutcome.answerRejected(firstResult.violations().get(0), toolsUsed, elapsedMs(startedAt));
        }

        CoachAction correctedAction = actionParser.parse(secondReply);
        if (correctedAction.kind() != CoachAction.Kind.ANSWER) {
            return CoachOutcome.answerRejected(firstResult.violations().get(0), toolsUsed, elapsedMs(startedAt));
        }

        String secondCandidate = tidy(correctedAction.answer(), turnContext);
        AnswerValidator.Result secondResult = validateSafely(secondCandidate, question, correctedTranscript, turnContext);
        if (secondResult.valid()) {
            return CoachOutcome.answered(secondCandidate, toolsUsed, elapsedMs(startedAt));
        }
        return CoachOutcome.answerRejected(secondResult.violations().get(0), toolsUsed, elapsedMs(startedAt));
    }

    /**
     * Runs {@link AnswerValidator} with the one guarantee the rest of this class depends on: it
     * never throws. A validator bug must degrade the single answer it was checking, not the turn,
     * so any exception is caught, only its class name is logged, and the answer is treated the same
     * as a validator that ran cleanly and rejected it.
     */
    private AnswerValidator.Result validateSafely(String answer, String question, List<String> transcript,
                                                  TurnContext turnContext) {
        try {
            return answerValidator.validate(answer, question, transcript, turnContext);
        } catch (RuntimeException ex) {
            LOG.warn("Answer validation failed with {}", ex.getClass().getSimpleName());
            return new AnswerValidator.Result(false, List.of("VALIDATION_ERROR"));
        }
    }

    /**
     * Turns the validator's codes into sentences the model can act on, the same purpose
     * {@code CoachReadingService.explain} serves for a rejected reading. A separate copy rather than
     * a shared one: the two validators use different codes for the same idea
     * ({@code NUMBER_NOT_IN_RECORDS} here against {@code NUMBER_NOT_IN_FACTS} there) and
     * {@code orchestrator} does not depend on {@code reading}, the same direction
     * {@code TurnContext}'s own Javadoc already documents.
     */
    private static String explainAnswerViolations(List<String> violations) {
        StringBuilder text = new StringBuilder();
        for (String violation : violations) {
            String code = violation.contains(":") ? violation.substring(0, violation.indexOf(':')) : violation;
            String detail = violation.contains(":") ? violation.substring(violation.indexOf(':') + 1) : "";
            String sentence = switch (code) {
                case "ANSWER_BLANK" -> "The answer was empty.";
                case "ANSWER_TOO_LONG" -> "The answer was longer than 700 characters.";
                case "NUMBER_NOT_IN_RECORDS" -> "The number " + detail + " is not in anything you were given.";
                case "INVALID_CITATION" -> "There is no fact numbered " + detail + ".";
                case "DIRECTION_NOT_IN_FACTS" -> "It described something as " + detail
                        + ", and the fact cited there does not say so.";
                case "MEASURE_MISMATCH" -> "It stated the per-session figure from fact " + detail
                        + " as though it were a weekly figure.";
                case "BANNED_PHRASE" -> "It gave advice or an instruction.";
                case "QUESTION_MARK" -> "It asked a question.";
                case "EXCLAMATION_MARK" -> "It used an exclamation mark.";
                case "DASH_AS_PUNCTUATION" -> "It used a dash as punctuation.";
                default -> "It broke the rule " + code + ".";
            };
            if (text.indexOf(sentence) < 0) {
                text.append(text.length() == 0 ? "" : " ").append(sentence);
            }
        }
        return "Your previous answer was not accepted. " + text
                + " Answer again from what you were already given, using only numbers and directions "
                + "that appear there, and correct the mistake.";
    }

    /** The one line that opens the transcript when the client says which page the user is on. */
    private static String surfaceLine(CoachSurface surface) {
        String pageName = switch (surface) {
            case HOME -> "Home";
            case WORKOUT -> "Workout";
            case NUTRITION -> "Nutrition";
            case PROGRESS -> "Progress";
            case PROFILE -> "Me";
        };
        return "The user is on the " + pageName + " page.";
    }

    /**
     * The pinned reading, shaped as a tool result so the system prompt's rule that every number must
     * come from a tool result covers it too, and recorded as though {@link #PINNED_READING_TOOL_NAME}
     * had actually been called so the trace shows what grounded the answer.
     */
    private static String pinnedReadingObservation(TurnContext.PinnedReading pinned) {
        StringBuilder body = new StringBuilder();
        body.append("The user is asking about this reading on their Workout page.");
        String headline = sanitizeFactPart(pinned.headline());
        String text = stripCitations(sanitizeFactPart(pinned.body()));
        if (!headline.isEmpty() || !text.isEmpty()) {
            body.append(System.lineSeparator()).append("Reading: ")
                    .append((headline + " " + text).strip());
        }
        body.append(System.lineSeparator()).append("Facts:");
        for (TurnContext.PinnedFact fact : pinned.facts()) {
            body.append(System.lineSeparator()).append(factLine(fact));
        }
        body.append(System.lineSeparator()).append("When you state a number from these facts, write "
                + "the fact's number in square brackets after it, like [2].");
        return "Result of " + PINNED_READING_TOOL_NAME + "({}):" + System.lineSeparator() + body;
    }

    /** One fact line: {@code [n] label: value unit. note. detail.}, with empty parts left out. */
    private static String factLine(TurnContext.PinnedFact fact) {
        StringBuilder line = new StringBuilder();
        line.append('[').append(fact.number()).append("] ");
        line.append(sanitizeFactPart(fact.label())).append(':').append(' ')
                .append(sanitizeFactPart(fact.value()));
        String unit = sanitizeFactPart(fact.unit());
        if (!unit.isEmpty()) {
            line.append(' ').append(unit);
        }
        line.append('.');
        String note = sanitizeFactPart(fact.note());
        if (!note.isEmpty()) {
            line.append(' ').append(note).append('.');
        }
        String detail = sanitizeFactPart(fact.detail());
        if (!detail.isEmpty()) {
            line.append(' ').append(detail).append('.');
        }
        return line.toString();
    }

    /**
     * A citation marker such as {@code [2]} removed from reading text before it is quoted back in a
     * tool observation, so the fact numbers a model sees always come from the facts list below it
     * rather than from a citation written for a different rendering of the same reading.
     */
    private static String stripCitations(String text) {
        return CITATION_MARKER.matcher(text).replaceAll("");
    }

    /**
     * Facts partly originate from user-named exercises, so every part is stripped of control
     * characters and newlines and cut to a bounded length before it can reach the transcript — a
     * label with an embedded newline and an instruction on the next line must still arrive as one
     * line the model reads as data, not as a second line of the prompt.
     */
    private static String sanitizeFactPart(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]+", " ").strip();
        return cleaned.length() > MAX_FACT_PART_LENGTH ? cleaned.substring(0, MAX_FACT_PART_LENGTH) : cleaned;
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

    /**
     * A listener that throws must not take the turn down with it: the answer is still being written
     * to the thread, and a client that has gone away can read it when it comes back.
     */
    private static void emit(CoachProgressListener listener, CoachProgressEvent event) {
        try {
            listener.onEvent(event);
        } catch (RuntimeException ex) {
            LOG.debug("Progress listener rejected an event", ex);
        }
    }

    private static int elapsedMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }
}
