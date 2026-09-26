package com.fitsync.recommendation.coach.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Asks a model for a session, and asks once more when the first answer breaks a rule.
 *
 * <p>Exactly one repair round, which is a decision rather than an arbitrary number. A model that
 * gets it right after being told precisely what was wrong is worth the second call; a model that
 * gets it wrong twice is not going to get it right on the third, and the user is waiting. The
 * ceiling also bounds the worst case: two inference calls, then the rules.
 *
 * <p>The repair prompt carries the violations verbatim, one per line, with the exercise each
 * concerns. Telling a model that its answer was invalid teaches it nothing; telling it that a
 * specific identifier does not resolve and which list to choose from is something it can act on.
 *
 * <p>Candidates are given to the model as a fixed list with their identifiers, and the prompt says
 * to use only those. That is what makes the first rule mostly pass: a model choosing from twenty
 * real rows invents far less than one asked to name exercises from memory.
 */
@Component
public class SessionDraftGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(SessionDraftGenerator.class);

    /**
     * The drafting prompt.
     *
     * <p>It ends with a filled-in example rather than a schema alone, which is not decoration. The
     * first live runs failed almost every time on one thing: the schema showed both {@code reps} and
     * {@code seconds} as "integer or null", so the model set both to null on every exercise and the
     * validator rejected the lot. A three-billion-parameter model copies the shape it is shown far
     * more reliably than it follows a rule it is told, so it is shown a correct one.
     */
    private static final String SYSTEM_PROMPT = """
            You design one training session for one person, using only the exercises you are given.

            Reply with ONE JSON object and nothing else, in exactly this shape:

            {
              "title": "Upper body push",
              "focus": ["CHEST", "SHOULDERS"],
              "estimatedMinutes": 45,
              "summary": "Two sentences on why this session, today, for this person.",
              "exercises": [
                {
                  "exerciseId": "3f1c2b90-4a55-4d21-9c0e-1b7a8e6d5f42",
                  "name": "Barbell Bench Press",
                  "sets": 4,
                  "reps": 8,
                  "seconds": null,
                  "restSeconds": 90,
                  "rationale": "Your main pressing movement, and you have not pressed this week."
                },
                {
                  "exerciseId": "9c4e7d13-2b88-4f60-a5d7-0e3f1a2c6b95",
                  "name": "Plank Hold",
                  "sets": 3,
                  "reps": null,
                  "seconds": 45,
                  "restSeconds": 45,
                  "rationale": "Finishes the session with bracing work that does not add fatigue."
                }
              ]
            }

            Rules you must follow:
            - Every exercise needs a number in EITHER "reps" OR "seconds". Never both null.
              Use "reps" for lifting movements and "seconds" only for holds and carries.
            - Use only exercise ids copied exactly from the candidate list. Never invent one.
            - Never use an exercise whose muscle group you were told to avoid.
            - Keep the total close to the requested length.
            - Write each rationale to the person, in one line.
            - The title must describe the session you designed. Do not reuse the example's title.
            """;

    private final ObjectProvider<ClaudeClient> claudeClient;
    private final SessionDraftValidator validator;
    private final ObjectMapper objectMapper;

    public SessionDraftGenerator(ObjectProvider<ClaudeClient> claudeClient,
                                 SessionDraftValidator validator,
                                 ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * @param request what the user asked for, in their words, or null for "something for today"
     * @return what happened, including the violations from the first attempt whether or not the
     *         repair fixed them
     */
    public Attempt generate(String request,
                            List<CatalogueExercise> candidates,
                            DraftContext context) {
        ClaudeClient client = claudeClient.getIfAvailable();
        if (client == null) {
            // Not a failure. The AI tier being off is a supported configuration, and the caller
            // falls through to the rules.
            return Attempt.modelUnavailable();
        }

        String prompt = firstPrompt(request, candidates, context);
        SessionDraft first;
        try {
            first = parse(client.complete(SYSTEM_PROMPT, prompt));
        } catch (RuntimeException ex) {
            LOG.warn("The model could not be reached for a session draft", ex);
            return Attempt.modelUnavailable();
        }

        if (first == null) {
            List<DraftViolation> unparseable = List.of(DraftViolation.session(
                    DraftViolation.Code.MALFORMED, "That reply was not a single JSON session object."));
            return repair(client, prompt, unparseable, candidates, context);
        }

        List<DraftViolation> violations = validator.validate(first, context);
        if (violations.isEmpty()) {
            return Attempt.passedFirstTime(withDerivedFocus(first, context));
        }
        return repair(client, prompt, violations, candidates, context);
    }

    private Attempt repair(ClaudeClient client,
                           String originalPrompt,
                           List<DraftViolation> violations,
                           List<CatalogueExercise> candidates,
                           DraftContext context) {
        String prompt = originalPrompt + System.lineSeparator() + System.lineSeparator()
                + "Your previous answer was rejected for these reasons:" + System.lineSeparator()
                + renderViolations(violations) + System.lineSeparator()
                + "Fix every one of them and reply with the corrected JSON object only.";

        SessionDraft repaired;
        try {
            repaired = parse(client.complete(SYSTEM_PROMPT, prompt));
        } catch (RuntimeException ex) {
            LOG.warn("The model could not be reached for a draft repair", ex);
            return Attempt.failed(violations);
        }

        if (repaired == null) {
            return Attempt.failed(violations);
        }
        List<DraftViolation> remaining = validator.validate(repaired, context);
        return remaining.isEmpty()
                ? Attempt.repaired(withDerivedFocus(repaired, context), violations)
                : Attempt.failed(violations);
    }

    /**
     * How many candidates the model is shown.
     *
     * <p>Fewer than the validator knows about. The pool is gathered wide so the rule fallback has
     * material and so a draft referencing something from an earlier turn still resolves, but a
     * three-billion-parameter model handed eighty rows chooses worse, not better, and the prompt
     * crowds out the instructions.
     */
    private static final int CANDIDATES_SHOWN = 24;

    private String firstPrompt(String request,
                               List<CatalogueExercise> candidates,
                               DraftContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Design a session of about ").append(context.targetMinutes())
                .append(" minutes.").append(System.lineSeparator());

        if (request != null && !request.isBlank()) {
            prompt.append("What they asked for: ").append(request.strip()).append(System.lineSeparator());
        }
        if (!context.availableEquipment().isEmpty()) {
            prompt.append("They train with: ").append(String.join(", ", context.availableEquipment()))
                    .append(System.lineSeparator());
        }
        if (!context.recentMuscleGroups().isEmpty()) {
            prompt.append("Trained yesterday, so avoid: ")
                    .append(String.join(", ", context.recentMuscleGroups()))
                    .append(System.lineSeparator());
        }

        prompt.append(System.lineSeparator()).append("Candidate exercises:").append(System.lineSeparator());
        for (CatalogueExercise candidate : candidates.subList(0, Math.min(candidates.size(), CANDIDATES_SHOWN))) {
            prompt.append(candidate.exerciseId()).append(" | ").append(candidate.name())
                    .append(" | ").append(candidate.primaryMuscleGroup())
                    .append(" | ").append(candidate.equipment())
                    .append(System.lineSeparator());
        }
        return prompt.toString();
    }

    /**
     * Replaces the model's stated focus with the one its exercises actually add up to.
     *
     * <p>Focus is derivable from the catalogue, so deriving it removes a class of mistake rather
     * than checking for it. A live run produced a card headed "Chest and shoulders" containing no
     * chest work, which is the same defect as any other stored claim that stopped matching what it
     * describes, and a validation rule would only have rejected the draft instead of fixing it.
     */
    private static SessionDraft withDerivedFocus(SessionDraft draft, DraftContext context) {
        java.util.Set<String> focus = new java.util.LinkedHashSet<>();
        for (DraftExercise exercise : draft.exercises()) {
            CatalogueExercise row = context.catalogue().get(exercise.exerciseId());
            if (row != null && !row.primaryMuscleGroup().isBlank()) {
                focus.add(row.primaryMuscleGroup().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return new SessionDraft(
                draft.title(), List.copyOf(focus), draft.estimatedMinutes(),
                draft.exercises(), draft.summary());
    }

    private static String renderViolations(List<DraftViolation> violations) {
        StringBuilder rendered = new StringBuilder();
        for (DraftViolation violation : violations) {
            rendered.append("- ").append(violation.message()).append(System.lineSeparator());
        }
        return rendered.toString().stripTrailing();
    }

    /**
     * @return the draft, or null when the reply was not a usable session object
     */
    private SessionDraft parse(String reply) {
        if (reply == null || reply.isBlank()) {
            LOG.debug("The model returned an empty draft reply");
            return null;
        }
        String json = extractObject(reply);
        if (json == null) {
            // Logged, because "it did not return JSON" is not a diagnosis. Knowing what a model
            // returns instead is how the prompt gets fixed, and it is the first thing anyone asks
            // when the fallback rate is high. Debug level and truncated: this is model output, it
            // can be long, and it is only wanted when someone is looking.
            LOG.debug("The model returned no JSON object. Reply began: {}", truncate(reply));
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<DraftExercise> exercises = new ArrayList<>();
            for (JsonNode item : node.path("exercises")) {
                exercises.add(readExercise(item));
            }
            List<String> focus = new ArrayList<>();
            for (JsonNode group : node.path("focus")) {
                focus.add(group.asText(""));
            }
            return new SessionDraft(
                    node.path("title").asText("Session"),
                    focus,
                    node.path("estimatedMinutes").asInt(0),
                    exercises,
                    node.path("summary").asText(""));
        } catch (JsonProcessingException ex) {
            LOG.debug("A draft reply did not parse as JSON. Reply began: {}", truncate(reply), ex);
            return null;
        }
    }

    /** Model output on a log line: bounded, and with anything that could end the line stripped. */
    private static String truncate(String reply) {
        String flattened = reply.replaceAll("[\r\n\t]", " ");
        return flattened.length() <= 400 ? flattened : flattened.substring(0, 400) + "...";
    }

    /**
     * An exercise as the model wrote it, with no correction applied.
     *
     * <p>A malformed identifier becomes null rather than an exception, so the validator reports it
     * as an unknown exercise and the repair round can be told about it. Fixing it here would hide
     * from the measurement exactly the mistake worth measuring.
     */
    private static DraftExercise readExercise(JsonNode item) {
        UUID exerciseId;
        try {
            String raw = item.path("exerciseId").asText(null);
            exerciseId = raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            exerciseId = null;
        }
        return new DraftExercise(
                exerciseId,
                item.path("name").asText(""),
                item.path("sets").asInt(0),
                item.hasNonNull("reps") ? Integer.valueOf(item.path("reps").asInt()) : null,
                item.hasNonNull("seconds") ? Integer.valueOf(item.path("seconds").asInt()) : null,
                item.path("restSeconds").asInt(0),
                item.path("rationale").asText(""));
    }

    /** Small models wrap JSON in prose or fences often enough to be worth handling here. */
    private static String extractObject(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : null;
    }

    /**
     * What one generation produced.
     *
     * @param draft             the usable session, or null when both attempts failed
     * @param repairUsed        true when the first attempt was rejected and the second passed
     * @param modelAvailable    false when the AI tier is off or unreachable
     * @param firstPassViolations what the first validation found, kept whatever happened next
     */
    public record Attempt(SessionDraft draft,
                          boolean repairUsed,
                          boolean modelAvailable,
                          List<DraftViolation> firstPassViolations) {

        public Attempt {
            firstPassViolations = firstPassViolations == null ? List.of() : List.copyOf(firstPassViolations);
        }

        @Override
        public List<DraftViolation> firstPassViolations() {
            return List.copyOf(firstPassViolations);
        }

        static Attempt passedFirstTime(SessionDraft draft) {
            return new Attempt(draft, false, true, List.of());
        }

        static Attempt repaired(SessionDraft draft, List<DraftViolation> firstPass) {
            return new Attempt(draft, true, true, firstPass);
        }

        static Attempt failed(List<DraftViolation> firstPass) {
            return new Attempt(null, true, true, firstPass);
        }

        static Attempt modelUnavailable() {
            return new Attempt(null, false, false, List.of());
        }

        public boolean succeeded() {
            return draft != null;
        }
    }
}
