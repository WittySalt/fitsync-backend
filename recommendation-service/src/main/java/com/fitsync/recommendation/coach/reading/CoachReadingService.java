package com.fitsync.recommendation.coach.reading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Serves the insight panel of the workout, nutrition and progress pages: a few measured facts, built
 * in pure code, and one short reading over them.
 *
 * <p>The reading is written by a rule writer from fixed templates by default. A model reading only
 * ever replaces it when one exists for exactly these facts and has already passed
 * {@link ReadingValidator} — the model is never trusted to be the only source of the reading, because
 * a small model has previously written a reading that passed every check and was still false. The
 * rule writer cannot make that mistake, because it has no free text to make it in.
 *
 * <p>Everything a page has in common lives here and everything that differs lives in a
 * {@link ReadingPage}: where its facts come from, how they are built, how its default reading is
 * written and what the model is told about it. The stored row is keyed by user and surface, so each
 * page has its own reading and its own generation.
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
 * answer and for the rule writers' own candidates alike.
 */
@Service
public class CoachReadingService {

    private static final Logger LOG = LoggerFactory.getLogger(CoachReadingService.class);

    /** How long a PENDING row is trusted to mean "a generation is genuinely still running". */
    private static final long PENDING_ABANDON_SECONDS = 60L;

    private final Map<ReadingSurface, ReadingPage> pages;
    private final ReadingValidator validator;
    private final CoachPageReadingStore store;
    private final ObjectProvider<ClaudeClient> claudeClientProvider;
    // A reader and a writer are immutable, which a shared ObjectMapper is not.
    private final ObjectReader answerReader;
    private final ObjectWriter factsWriter;
    private final Clock clock;
    private final ExecutorService executor;

    public CoachReadingService(WorkoutReadingPage workoutPage,
                               NutritionReadingPage nutritionPage,
                               ProgressReadingPage progressPage,
                               ReadingValidator validator,
                               CoachPageReadingStore store,
                               ObjectProvider<ClaudeClient> claudeClientProvider,
                               ObjectMapper objectMapper,
                               Clock clock,
                               @Qualifier("coachReadingExecutor") ExecutorService executor) {
        this.pages = Map.of(
                ReadingSurface.WORKOUT, workoutPage,
                ReadingSurface.NUTRITION, nutritionPage,
                ReadingSurface.PROGRESS, progressPage);
        this.validator = validator;
        this.store = store;
        this.claudeClientProvider = claudeClientProvider;
        this.answerReader = objectMapper.readerFor(ModelAnswer.class);
        this.factsWriter = objectMapper.writer();
        this.clock = clock;
        this.executor = executor;
    }

    /** {@link #pinnedReading} for the workout page. */
    public Optional<PinnedReading> pinnedWorkoutReading(UUID userId, String bearerToken, String timezone,
                                                         String readingId) {
        return pinnedReading(ReadingSurface.WORKOUT, userId, bearerToken, timezone, readingId);
    }

    /**
     * The reading a coach turn may cite, resolved fresh and matched against the id the client sent.
     *
     * <p>This is the read side of the security rule the coach message context is built on: a client
     * sends only a page name and this opaque id, never facts or reading text, so the server is the
     * only party that ever decides what a citation refers to. The facts are worked out exactly as
     * {@link #reading} works them out; the difference is what happens once they exist.
     *
     * <p>Unlike {@link #reading}, this method has no side effect: it never claims a
     * {@code PENDING} row and never enqueues a generation. A coach turn needs an answer regardless of
     * whether a model reading exists yet, so there is nothing here worth starting a generation over —
     * the page itself is what does that, the next time its own GET runs.
     *
     * @return the matched reading, or empty when the id does not match, there are too few facts, the
     *         period has nothing to read, or the upstream could not be read at all. Never throws for
     *         an unavailable upstream — a coach turn must still be answered without the reading
     *         rather than fail because a dependency is down.
     */
    public Optional<PinnedReading> pinnedReading(ReadingSurface surface, UUID userId, String bearerToken,
                                                  String timezone, String readingId) {
        if (readingId == null) {
            return Optional.empty();
        }

        ReadingPage page = pages.get(surface);
        ReadingPage.PageSnapshot snapshot;
        try {
            snapshot = page.snapshot(bearerToken, timezone);
        } catch (RuntimeException ex) {
            LOG.warn("event=coach_pinned_reading_upstream_failed reason={}",
                    String.valueOf(ex.getClass().getSimpleName()).replace("\r", "_").replace("\n", "_"));
            return Optional.empty();
        }
        if (snapshot == null || snapshot.nothingToRead()) {
            return Optional.empty();
        }

        List<ReadingFact> facts = snapshot.facts();
        if (facts.size() < page.minimumFacts()) {
            return Optional.empty();
        }

        String hash = hash(snapshot);
        String computedId = hash.substring(0, 16);
        if (!CoachPageReadingStore.hashesMatch(computedId, readingId)) {
            return Optional.empty();
        }

        Optional<CoachPageReadingEntity> existing = store.find(userId, surface);
        Instant now = Instant.now(clock);
        if (existing.isPresent() && CoachPageReadingStore.hashesMatch(hash, existing.get().getFactsHash())) {
            CoachPageReadingEntity row = existing.get();
            boolean expired = row.getExpiresAt() != null && now.isAfter(row.getExpiresAt());
            if (!expired && row.getOutcome() == ReadingOutcome.ACCEPTED) {
                return Optional.of(new PinnedReading(computedId, row.getHeadline(), row.getBody(), row.getSource(),
                        facts, snapshot.period(), snapshot.periodStart(), snapshot.periodEnd()));
            }
        }

        Optional<RuleReadingWriter.RuleReading> ruleReading = snapshot.ruleReading().get();
        if (ruleReading.isPresent()) {
            RuleReadingWriter.RuleReading rule = ruleReading.get();
            return Optional.of(new PinnedReading(computedId, rule.headline(), rule.body(), "RULES",
                    facts, snapshot.period(), snapshot.periodStart(), snapshot.periodEnd()));
        }
        return Optional.of(new PinnedReading(computedId, null, null, null,
                facts, snapshot.period(), snapshot.periodStart(), snapshot.periodEnd()));
    }

    /**
     * A reading a coach turn is allowed to cite: the id it was matched against, its text (absent when
     * neither a model nor the rule writer has one), and the facts it was built from.
     *
     * <p>Deliberately shaped without a dependency on the orchestrator: {@code coach.orchestrator}
     * maps this into its own {@code TurnContext.PinnedReading} rather than this type reaching the
     * agent loop directly, which keeps the two packages decoupled from each other's internals the
     * same way {@link CoachReadingResponse} is already decoupled from the orchestrator.
     */
    public record PinnedReading(String readingId, String headline, String body, String source, List<ReadingFact> facts,
                                String period, LocalDate periodStart, LocalDate periodEnd) {

        /** True when the facts describe the current week so far, false for any other period. */
        public boolean describesCurrentWeek() {
            return "THIS_WEEK".equals(period);
        }

        /** True when the period is a calendar week, the current one or the last full one. */
        public boolean describesAWeek() {
            return "THIS_WEEK".equals(period) || "PREVIOUS_WEEK".equals(period);
        }

        public PinnedReading {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        @Override
        public List<ReadingFact> facts() {
            return List.copyOf(facts);
        }
    }

    public CoachReadingResponse workoutReading(UUID userId, String bearerToken, String timezone) {
        return reading(ReadingSurface.WORKOUT, userId, bearerToken, timezone);
    }

    public CoachReadingResponse nutritionReading(UUID userId, String bearerToken, String timezone) {
        return reading(ReadingSurface.NUTRITION, userId, bearerToken, timezone);
    }

    public CoachReadingResponse progressReading(UUID userId, String bearerToken, String timezone) {
        return reading(ReadingSurface.PROGRESS, userId, bearerToken, timezone);
    }

    /** The reading of one page, the same steps for every surface. */
    public CoachReadingResponse reading(ReadingSurface surface, UUID userId, String bearerToken, String timezone) {
        ReadingPage page = pages.get(surface);
        ReadingPage.PageSnapshot snapshot = page.snapshot(bearerToken, timezone);
        if (snapshot == null) {
            throw new ReadingUnavailableException(page.unavailableMessage());
        }

        List<ReadingFact> facts = snapshot.facts();
        if (snapshot.nothingToRead() || facts.size() < page.minimumFacts()) {
            return response(surface, ReadingStatus.EMPTY, null, snapshot, null);
        }

        String hash = hash(snapshot);
        String readingId = hash.substring(0, 16);
        Instant now = Instant.now(clock);

        Optional<CoachPageReadingEntity> existing = store.find(userId, surface);
        if (existing.isPresent() && CoachPageReadingStore.hashesMatch(hash, existing.get().getFactsHash())) {
            CoachPageReadingEntity row = existing.get();
            boolean expired = row.getExpiresAt() != null && now.isAfter(row.getExpiresAt());
            if (!expired && row.getOutcome() == ReadingOutcome.ACCEPTED) {
                return ready(surface, snapshot, readingId, row);
            }
        }

        // Deciding whether a model generation is owed never changes what this call returns; it only
        // decides whether the client should be told WRITING or RESTING on the rare turn the rule
        // writer itself has nothing to say.
        boolean modelGenerationInFlight = decideModelGeneration(userId, hash, page, snapshot, existing, now);

        Optional<RuleReadingWriter.RuleReading> ruleReading = snapshot.ruleReading().get();
        if (ruleReading.isPresent()) {
            RuleReadingWriter.RuleReading rule = ruleReading.get();
            ModelReadingView reading = new ModelReadingView(rule.headline(), rule.body(), rule.citations(), "RULES", now);
            return response(surface, ReadingStatus.READY, readingId, snapshot, reading);
        }
        return response(surface, modelGenerationInFlight ? ReadingStatus.WRITING : ReadingStatus.RESTING,
                readingId, snapshot, null);
    }

    /**
     * The claim-and-enqueue decision: a fresh rejection or a fresh generation already in flight
     * means there is nothing to do, an abandoned or missing row means a fresh attempt is claimed and
     * enqueued, and no configured {@link ClaudeClient} means there is nothing that could be enqueued
     * at all.
     *
     * @return whether a model generation is now in flight for these facts (already running, or just
     *         started here) — the only thing about this decision the caller still needs to know, now
     *         that the response itself is decided by the rule writer instead
     */
    private boolean decideModelGeneration(UUID userId, String hash, ReadingPage page,
                                          ReadingPage.PageSnapshot snapshot,
                                          Optional<CoachPageReadingEntity> existing, Instant now) {
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

        List<ReadingFact> facts = snapshot.facts();
        String period = snapshot.period();
        store.claimPending(userId, page.surface(), hash, now);
        executor.execute(() -> generate(userId, page, hash, facts, period));
        return true;
    }

    /**
     * The generation itself: one attempt, and on a failed validation, one repair attempt that is
     * told exactly what was wrong. Runs entirely off the request thread.
     */
    private void generate(UUID userId, ReadingPage page, String factsHash, List<ReadingFact> facts, String period) {
        try {
            generateOrReject(userId, page, factsHash, facts, period);
        } catch (RuntimeException ex) {
            // Anything unexpected ends as a rejection. Without this the row would stay pending,
            // and every visit after the sixty seconds would start the same failing work again.
            LOG.warn("event=coach_reading_generation_failed reason={}",
                    String.valueOf(ex.getClass().getSimpleName()).replace("\r", "_").replace("\n", "_"));
            store.persistRejected(userId, page.surface(), factsHash, Instant.now(clock),
                    CoachPageReadingStore.REJECTED_TTL);
        }
    }

    private void generateOrReject(UUID userId, ReadingPage page, String factsHash, List<ReadingFact> facts,
                                  String period) {
        ClaudeClient client = claudeClientProvider.getIfAvailable();
        if (client == null) {
            // The AI tier was switched off between the GET and this running; nothing to do, and the
            // row is simply picked up as abandoned on the next request past the sixty-second window.
            return;
        }

        ReadingSurface surface = page.surface();
        String systemPrompt = page.systemPrompt();
        String prompt = promptFor(facts, period);
        Attempt first = tryGenerate(client, systemPrompt, prompt);
        if (!first.reached()) {
            // A model that cannot be reached has not failed any check. It is asked again after a
            // short wait, so the reading returns soon after the model does.
            store.persistRejected(userId, surface, factsHash, Instant.now(clock),
                    CoachPageReadingStore.UNREACHABLE_TTL);
            logOutcome(userId, surface, "UNREACHABLE");
            return;
        }
        ReadingValidator.Result firstResult = validate(first, facts);
        if (firstResult != null && firstResult.valid()) {
            accept(userId, surface, factsHash, first.answer(), firstResult);
            return;
        }

        List<String> violations = firstResult != null ? firstResult.violations() : List.of("ANSWER_NOT_JSON");
        String repairPrompt = prompt + "\n\nYour previous answer was not accepted. "
                + explain(violations) + " Answer again in the same JSON shape.";
        Attempt second = tryGenerate(client, systemPrompt, repairPrompt);
        ReadingValidator.Result secondResult = validate(second, facts);
        if (secondResult != null && secondResult.valid()) {
            accept(userId, surface, factsHash, second.answer(), secondResult);
            return;
        }

        store.persistRejected(userId, surface, factsHash, Instant.now(clock),
                second.reached() ? CoachPageReadingStore.REJECTED_TTL : CoachPageReadingStore.UNREACHABLE_TTL);
        logOutcome(userId, surface, second.reached() ? "REJECTED" : "UNREACHABLE");
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

    private void accept(UUID userId, ReadingSurface surface, String factsHash, ModelAnswer answer,
                        ReadingValidator.Result result) {
        store.persistAccepted(userId, surface, factsHash,
                answer.headline(), answer.body(), result.citations(), Instant.now(clock));
        logOutcome(userId, surface, "ACCEPTED");
    }

    private Attempt tryGenerate(ClaudeClient client, String systemPrompt, String userPrompt) {
        String raw;
        try {
            raw = client.complete(systemPrompt, userPrompt);
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

    private CoachReadingResponse ready(ReadingSurface surface, ReadingPage.PageSnapshot snapshot, String readingId,
                                       CoachPageReadingEntity row) {
        ModelReadingView reading = new ModelReadingView(
                row.getHeadline(), row.getBody(), parseCitations(row.getCitations()), row.getSource(), row.getGeneratedAt());
        return response(surface, ReadingStatus.READY, readingId, snapshot, reading);
    }

    private static CoachReadingResponse response(ReadingSurface surface, ReadingStatus status, String readingId,
                                                 ReadingPage.PageSnapshot snapshot, ModelReadingView reading) {
        return new CoachReadingResponse(surface, status, readingId, snapshot.period(), snapshot.periodStart(),
                snapshot.periodEnd(), snapshot.facts(), reading, snapshot.lifts());
    }

    private static List<Integer> parseCitations(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(Integer::valueOf).toList();
    }

    private void logOutcome(UUID userId, ReadingSurface surface, String outcome) {
        // Every value is sanitised inline against CRLF injection (CWE-117), the same way
        // WeeklyNarrativeService does it: findsecbugs only accepts replace() directly in the
        // dataflow to the log sink, not through a helper method. userId is a real identifier and
        // surface and outcome are always this class's own values, but the detector flags any
        // non-constant argument regardless, so all three are cleaned the same way.
        LOG.info("event=coach_reading_outcome userId={} surface={} outcome={}",
                String.valueOf(userId).replace("\r", "_").replace("\n", "_"),
                String.valueOf(surface).replace("\r", "_").replace("\n", "_"),
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
        return hash(insight.period(), insight.periodStart(), insight.periodEnd(), facts);
    }

    private static String hash(ReadingPage.PageSnapshot snapshot) {
        return hash(snapshot.period(), snapshot.periodStart(), snapshot.periodEnd(), snapshot.facts());
    }

    static String hash(String period, LocalDate periodStart, LocalDate periodEnd, List<ReadingFact> facts) {
        StringBuilder canonical = new StringBuilder();
        char separator = '\u001F';
        canonical.append(period).append(separator);
        canonical.append(periodStart).append(separator);
        canonical.append(periodEnd).append(separator);
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
