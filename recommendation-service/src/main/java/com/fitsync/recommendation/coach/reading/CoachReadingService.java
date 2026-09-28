package com.fitsync.recommendation.coach.reading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Serves the workout page's insight panel: four measured facts, built in pure code, and one short
 * reading over them.
 *
 * <p>The reading is written by {@link RuleReadingWriter} from fixed templates by default. A model
 * reading only ever replaces it when one exists for exactly these facts and has already passed
 * {@link ReadingValidator} — the model is never trusted to be the only source of the reading, because
 * a small model has previously written a reading that passed every check and was still false. The
 * rule writer cannot make that mistake, because it has no free text to make it in.
 *
 * <p>The GET this backs is cheap by design, because a client is expected to poll it while a model
 * reading is being written in the background. It never runs the model itself; it reads the current
 * cached row, decides what that row means (nothing yet, in flight, already failed, or ready), and —
 * the one side effect it does have — enqueues a generation when one is owed and none is already
 * running. Everything that touches the model happens on {@code coachReadingExecutor}, off this
 * thread, and none of it changes what this call returns: that is decided by the rule writer, or by an
 * already-accepted model reading found on the way in.
 *
 * <p>Two rules matter more than anything the model was told, and both are enforced here rather than
 * trusted to the prompt: a reading never tells the user what to do, and every number in it is one of
 * the measured facts. {@link ReadingValidator} is where both are actually checked, for the model's
 * answer and for the rule writer's own candidates alike.
 */
@Service
public class CoachReadingService {

    private static final Logger LOG = LoggerFactory.getLogger(CoachReadingService.class);

    /** How long a PENDING row is trusted to mean "a generation is genuinely still running". */
    private static final long PENDING_ABANDON_SECONDS = 60L;

    private static final String SYSTEM_PROMPT = """
            You are FitSync's coach, noticing patterns in one page of pre-computed facts. You never \
            give advice, an instruction, a suggestion or a question - you only observe what the facts \
            show together, which the numbers alone do not.

            Answer with exactly one JSON object and nothing else: {"headline": "...", "body": "..."}

            Rules for the headline:
            - One sentence, at most 80 characters.
            - It does not need to contain a number.

            Rules for the body:
            - One or two sentences, at most 320 characters, on a single line.
            - It connects at least two of the facts and says what they show together.
            - Every number you write must be copied exactly from the facts you were given - do not \
            calculate a percentage, ratio or difference yourself.
            - Immediately after each number or claim taken from a fact, write that fact's number in \
            square brackets, like [2].

            Style:
            - Second person, plain English.
            - No advice, no instruction, no suggestion, no question, no exclamation mark, no emoji, \
            no medical statement.
            - No dash used as punctuation.
            - Say only what happened. Never write that something did not happen, and do not use \
            the words no, not, never or without.
            - A fact whose label ends in "record" means that a record was set.
            - Say that something went up, went down or stayed the same only when the note of a \
            fact you cite in that sentence says Up, Down or Unchanged, or that fact is a record.
            - Every sentence of the body cites at least one fact.

            Facts are given as a JSON array, each with a number, a label, a value, a unit, a short \
            note and a longer detail. The period label tells you whether this covers the current or \
            the previous week.

            The example below shows the form only. Its numbers belong to someone else, so none \
            of them may appear in your answer.

            Example input:
            period: THIS_WEEK
            facts: [
              {"number":1,"label":"Weight moved per session","value":"2,480","unit":"kg","note":"Down 6%","detail":"2,640 kg the week before"},
              {"number":2,"label":"Session length","value":"45","unit":"min","note":"Down 10 min","detail":"55 min the week before"},
              {"number":3,"label":"Barbell Deadlift record","value":"80","unit":"kg","note":"Thursday","detail":"75 kg before"},
              {"number":4,"label":"Sessions with leg work","value":"3","unit":"of 5","note":"","detail":"Back Squat"}
            ]

            Example output:
            {"headline": "Shorter sessions moved less weight and still set a record.", "body": "Each session moved 2,480 kg [1] in 45 min [2], both lower than the week before. Thursday still brought a deadlift of 80 kg [3], up from 75 kg [3]."}""";

    private final WorkoutCatalogueClient workoutClient;
    // The builder is pure and holds no state, so the service keeps its own.
    private final ReadingFactsBuilder factsBuilder = new ReadingFactsBuilder();
    private final ReadingValidator validator;
    // Resolved through ObjectProvider rather than stored directly, the same fix
    // SpringKafkaEventProducer uses elsewhere in this codebase, because SpotBugs' EI_EXPOSE_REP2
    // flags a constructor-injected singleton bean stored as a plain field regardless of whether the
    // bean itself is stateless.
    private final ObjectProvider<RuleReadingWriter> ruleReadingWriterProvider;
    private final CoachPageReadingStore store;
    private final ObjectProvider<ClaudeClient> claudeClientProvider;
    // A reader and a writer are immutable, which a shared ObjectMapper is not.
    private final ObjectReader answerReader;
    private final ObjectWriter factsWriter;
    private final Clock clock;
    private final ExecutorService executor;

    public CoachReadingService(WorkoutCatalogueClient workoutClient,
                               ReadingValidator validator,
                               ObjectProvider<RuleReadingWriter> ruleReadingWriterProvider,
                               CoachPageReadingStore store,
                               ObjectProvider<ClaudeClient> claudeClientProvider,
                               ObjectMapper objectMapper,
                               Clock clock,
                               @Qualifier("coachReadingExecutor") ExecutorService executor) {
        this.workoutClient = workoutClient;
        this.validator = validator;
        this.ruleReadingWriterProvider = ruleReadingWriterProvider;
        this.store = store;
        this.claudeClientProvider = claudeClientProvider;
        this.answerReader = objectMapper.readerFor(ModelAnswer.class);
        this.factsWriter = objectMapper.writer();
        this.clock = clock;
        this.executor = executor;
    }

    public CoachReadingResponse workoutReading(UUID userId, String bearerToken, String timezone) {
        TrainingInsight insight = workoutClient.trainingInsight(bearerToken, timezone);
        if (insight == null) {
            throw new ReadingUnavailableException(
                    "The workout service could not be read, so there is nothing to build today's reading from.");
        }

        List<ReadingFact> facts = factsBuilder.build(insight);
        if (TrainingInsight.PERIOD_NONE.equals(insight.period()) || facts.size() < 2) {
            return new CoachReadingResponse(ReadingSurface.WORKOUT, ReadingStatus.EMPTY, null,
                    insight.period(), insight.periodStart(), insight.periodEnd(), facts, null, insight.lifts());
        }

        String hash = hash(insight, facts);
        String readingId = hash.substring(0, 16);
        Instant now = Instant.now(clock);

        Optional<CoachPageReadingEntity> existing = store.find(userId, ReadingSurface.WORKOUT);
        if (existing.isPresent() && CoachPageReadingStore.hashesMatch(hash, existing.get().getFactsHash())) {
            CoachPageReadingEntity row = existing.get();
            boolean expired = row.getExpiresAt() != null && now.isAfter(row.getExpiresAt());
            if (!expired && row.getOutcome() == ReadingOutcome.ACCEPTED) {
                return ready(insight, facts, readingId, row);
            }
        }

        // Deciding whether a model generation is owed never changes what this call returns; it only
        // decides whether the client should be told WRITING or RESTING on the rare turn the rule
        // writer itself has nothing to say.
        boolean modelGenerationInFlight = decideModelGeneration(userId, hash, facts, insight, existing, now);

        RuleReadingWriter ruleReadingWriter = ruleReadingWriterProvider.getObject();
        Optional<RuleReadingWriter.RuleReading> ruleReading = ruleReadingWriter.write(insight, facts);
        if (ruleReading.isPresent()) {
            return readyWithRules(insight, facts, readingId, ruleReading.get(), now);
        }
        return modelGenerationInFlight ? writing(insight, facts, readingId) : resting(insight, facts, readingId);
    }

    /**
     * The unchanged claim-and-enqueue decision from before rule-written readings existed: a fresh
     * rejection or a fresh generation already in flight means there is nothing to do, an abandoned or
     * missing row means a fresh attempt is claimed and enqueued, and no configured {@link ClaudeClient}
     * means there is nothing that could be enqueued at all.
     *
     * @return whether a model generation is now in flight for these facts (already running, or just
     *         started here) — the only thing about this decision the caller still needs to know, now
     *         that the response itself is decided by the rule writer instead
     */
    private boolean decideModelGeneration(UUID userId, String hash, List<ReadingFact> facts,
                                          TrainingInsight insight, Optional<CoachPageReadingEntity> existing,
                                          Instant now) {
        if (existing.isPresent() && CoachPageReadingStore.hashesMatch(hash, existing.get().getFactsHash())) {
            CoachPageReadingEntity row = existing.get();
            boolean expired = row.getExpiresAt() != null && now.isAfter(row.getExpiresAt());
            if (!expired) {
                if (row.getOutcome() == ReadingOutcome.REJECTED) {
                    return false;
                }
                if (row.getOutcome() == ReadingOutcome.PENDING
                        && row.getStartedAt() != null
                        && Duration.between(row.getStartedAt(), now).getSeconds() < PENDING_ABANDON_SECONDS) {
                    return true;
                }
                // A PENDING row older than the window is abandoned - a worker died or was never
                // scheduled - and falls through to be reclaimed below rather than wedging this page.
            }
        }

        ClaudeClient client = claudeClientProvider.getIfAvailable();
        if (client == null) {
            return false;
        }

        store.claimPending(userId, ReadingSurface.WORKOUT, hash, now);
        executor.execute(() -> generate(userId, hash, facts, insight.period()));
        return true;
    }

    /**
     * The generation itself: one attempt, and on a failed validation, one repair attempt that is
     * told exactly what was wrong. Runs entirely off the request thread.
     */
    private void generate(UUID userId, String factsHash, List<ReadingFact> facts, String period) {
        try {
            generateOrReject(userId, factsHash, facts, period);
        } catch (RuntimeException ex) {
            // Anything unexpected ends as a rejection. Without this the row would stay pending,
            // and every visit after the sixty seconds would start the same failing work again.
            LOG.warn("event=coach_reading_generation_failed reason={}",
                    String.valueOf(ex.getClass().getSimpleName()).replace("\r", "_").replace("\n", "_"));
            store.persistRejected(userId, ReadingSurface.WORKOUT, factsHash, Instant.now(clock),
                    CoachPageReadingStore.REJECTED_TTL);
        }
    }

    private void generateOrReject(UUID userId, String factsHash, List<ReadingFact> facts, String period) {
        ClaudeClient client = claudeClientProvider.getIfAvailable();
        if (client == null) {
            // The AI tier was switched off between the GET and this running; nothing to do, and the
            // row is simply picked up as abandoned on the next request past the sixty-second window.
            return;
        }

        String prompt = promptFor(facts, period);
        Attempt first = tryGenerate(client, prompt);
        if (!first.reached()) {
            // A model that cannot be reached has not failed any check. It is asked again after a
            // short wait, so the reading returns soon after the model does.
            store.persistRejected(userId, ReadingSurface.WORKOUT, factsHash, Instant.now(clock),
                    CoachPageReadingStore.UNREACHABLE_TTL);
            logOutcome(userId, "UNREACHABLE");
            return;
        }
        ReadingValidator.Result firstResult = validate(first, facts);
        if (firstResult != null && firstResult.valid()) {
            accept(userId, factsHash, first.answer(), firstResult);
            return;
        }

        List<String> violations = firstResult != null ? firstResult.violations() : List.of("ANSWER_NOT_JSON");
        String repairPrompt = prompt + "\n\nYour previous answer was not accepted. "
                + explain(violations) + " Answer again in the same JSON shape.";
        Attempt second = tryGenerate(client, repairPrompt);
        ReadingValidator.Result secondResult = validate(second, facts);
        if (secondResult != null && secondResult.valid()) {
            accept(userId, factsHash, second.answer(), secondResult);
            return;
        }

        store.persistRejected(userId, ReadingSurface.WORKOUT, factsHash, Instant.now(clock),
                second.reached() ? CoachPageReadingStore.REJECTED_TTL : CoachPageReadingStore.UNREACHABLE_TTL);
        logOutcome(userId, second.reached() ? "REJECTED" : "UNREACHABLE");
    }

    /**
     * Turns the validator's codes into sentences. A small model does little with a bare code, and
     * the sentence names the rule in the same words the system prompt used.
     */
    static String explain(List<String> violations) {
        StringBuilder text = new StringBuilder();
        for (String violation : violations) {
            String code = violation.contains(":") ? violation.substring(0, violation.indexOf(':')) : violation;
            String detail = violation.contains(":") ? violation.substring(violation.indexOf(':') + 1) : "";
            String sentence = switch (code) {
                case "NUMBER_NOT_IN_FACTS" -> "The number " + detail + " is not in the facts.";
                case "INVALID_CITATION" -> "There is no fact numbered " + detail + ".";
                case "NO_CITATION" -> "The body cited no fact in square brackets.";
                case "NEGATION" -> "It said that something did not happen.";
                case "DIRECTION_NOT_IN_FACTS" -> "It described something as " + detail
                        + ", and no fact cited there says so.";
                case "SENTENCE_WITHOUT_CITATION" -> "Sentence " + detail + " of the body cited no fact.";
                case "BANNED_PHRASE" -> "It gave advice or an instruction.";
                case "QUESTION_MARK" -> "It asked a question.";
                case "EXCLAMATION_MARK" -> "It used an exclamation mark.";
                case "DASH_AS_PUNCTUATION" -> "It used a dash as punctuation.";
                case "HEADLINE_TOO_LONG" -> "The headline was longer than 80 characters.";
                case "BODY_TOO_LONG" -> "The body was longer than 320 characters.";
                case "ANSWER_NOT_JSON" -> "It was not a single JSON object.";
                default -> "It broke the rule " + code + ".";
            };
            if (text.indexOf(sentence) < 0) {
                text.append(text.length() == 0 ? "" : " ").append(sentence);
            }
        }
        return text.toString();
    }

    private ReadingValidator.Result validate(Attempt attempt, List<ReadingFact> facts) {
        ModelAnswer answer = attempt.answer();
        return answer == null ? null : validator.validate(answer.headline(), answer.body(), facts);
    }

    /**
     * One call to the model. {@code reached} is false when the call itself failed, and an answer
     * that arrived but could not be read as JSON has {@code reached} true and no answer.
     */
    private record Attempt(ModelAnswer answer, boolean reached) {
    }

    private void accept(UUID userId, String factsHash, ModelAnswer answer, ReadingValidator.Result result) {
        store.persistAccepted(userId, ReadingSurface.WORKOUT, factsHash,
                answer.headline(), answer.body(), result.citations(), Instant.now(clock));
        logOutcome(userId, "ACCEPTED");
    }

    private Attempt tryGenerate(ClaudeClient client, String userPrompt) {
        String raw;
        try {
            raw = client.complete(SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException ex) {
            LOG.warn("event=coach_reading_model_call_failed reason={}",
                    String.valueOf(ex.getMessage()).replace("\r", "_").replace("\n", "_"));
            return new Attempt(null, false);
        }
        return new Attempt(parseAnswer(raw), true);
    }

    /** Strips a Markdown code fence if the model wrapped its JSON in one, then parses leniently. */
    private ModelAnswer parseAnswer(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        try {
            return answerReader.readValue(text);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * The prompt carries only the numbered facts (label, value, unit, note, detail) and the period
     * label - never the user id, never the token, never anything the model could echo back that
     * identifies who this is about.
     */
    private String promptFor(List<ReadingFact> facts, String period) {
        List<Map<String, Object>> compact = facts.stream().map(fact -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("number", fact.number());
            map.put("label", fact.label());
            map.put("value", fact.value());
            map.put("unit", fact.unit());
            map.put("note", fact.note());
            map.put("detail", fact.detail());
            return map;
        }).toList();
        try {
            return "period: " + period + "\nfacts: " + factsWriter.writeValueAsString(compact);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise reading facts", ex);
        }
    }

    private CoachReadingResponse ready(TrainingInsight insight, List<ReadingFact> facts, String readingId,
                                       CoachPageReadingEntity row) {
        ModelReadingView reading = new ModelReadingView(
                row.getHeadline(), row.getBody(), parseCitations(row.getCitations()), row.getSource(), row.getGeneratedAt());
        return new CoachReadingResponse(ReadingSurface.WORKOUT, ReadingStatus.READY, readingId,
                insight.period(), insight.periodStart(), insight.periodEnd(), facts, reading, insight.lifts());
    }

    private CoachReadingResponse readyWithRules(TrainingInsight insight, List<ReadingFact> facts, String readingId,
                                                RuleReadingWriter.RuleReading rule, Instant now) {
        ModelReadingView reading = new ModelReadingView(rule.headline(), rule.body(), rule.citations(), "RULES", now);
        return new CoachReadingResponse(ReadingSurface.WORKOUT, ReadingStatus.READY, readingId,
                insight.period(), insight.periodStart(), insight.periodEnd(), facts, reading, insight.lifts());
    }

    private CoachReadingResponse writing(TrainingInsight insight, List<ReadingFact> facts, String readingId) {
        return new CoachReadingResponse(ReadingSurface.WORKOUT, ReadingStatus.WRITING, readingId,
                insight.period(), insight.periodStart(), insight.periodEnd(), facts, null, insight.lifts());
    }

    private CoachReadingResponse resting(TrainingInsight insight, List<ReadingFact> facts, String readingId) {
        return new CoachReadingResponse(ReadingSurface.WORKOUT, ReadingStatus.RESTING, readingId,
                insight.period(), insight.periodStart(), insight.periodEnd(), facts, null, insight.lifts());
    }

    private static List<Integer> parseCitations(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(Integer::valueOf).toList();
    }

    private void logOutcome(UUID userId, String outcome) {
        // Both values are sanitised inline against CRLF injection (CWE-117), the same way
        // WeeklyNarrativeService does it: findsecbugs only accepts replace() directly in the
        // dataflow to the log sink, not through a helper method. userId is a real identifier and
        // outcome is always one of this class's own literals, but the detector flags any
        // non-constant argument regardless, so both are cleaned the same way.
        LOG.info("event=coach_reading_outcome userId={} surface=WORKOUT outcome={}",
                String.valueOf(userId).replace("\r", "_").replace("\n", "_"),
                String.valueOf(outcome).replace("\r", "_").replace("\n", "_"));
    }

    /**
     * SHA-256 over the facts that determine what a reading says, so a changed page starts a new
     * generation and an unchanged one keeps serving what was already validated. The separator is a
     * control character that cannot occur in any of the joined values, so two different fact sets
     * cannot be shuffled into producing the same string.
     *
     * <p>Package-private rather than private so {@code CoachReadingServiceTests} can pre-compute the
     * hash for a fixture it wants to seed directly into the repository — the only way to construct
     * the "abandoned PENDING row" scenario without a background thread actually going stale.
     */
    static String hash(TrainingInsight insight, List<ReadingFact> facts) {
        StringBuilder canonical = new StringBuilder();
        char separator = '\u001F';
        canonical.append(insight.period()).append(separator);
        canonical.append(insight.periodStart()).append(separator);
        canonical.append(insight.periodEnd()).append(separator);
        for (ReadingFact fact : facts) {
            canonical.append(fact.key()).append(separator)
                    .append(fact.value()).append(separator)
                    .append(fact.unit()).append(separator)
                    .append(fact.note()).append(separator)
                    .append(fact.detail()).append(separator);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
