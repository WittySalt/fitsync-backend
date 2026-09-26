package com.fitsync.recommendation.coach.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Generation, against a scripted model.
 *
 * <p>What needs proving is the shape of the pipeline rather than the quality of any model's prose:
 * that a good answer is taken as it is, that a bad one earns exactly one corrective round, that the
 * correction carries what was actually wrong, and that two failures give up rather than looping. A
 * scripted model exercises each of those deterministically, which a real one cannot.
 *
 * <p>The prompts the model receives are asserted too, because the repair round is only worth having
 * if it says something specific enough to act on.
 */
class SessionDraftGeneratorTests {

    private static final UUID BENCH = UUID.nameUUIDFromBytes("bench".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID ROW = UUID.nameUUIDFromBytes("row".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionDraftValidator validator = new SessionDraftValidator();
    private final List<String> prompts = new ArrayList<>();

    @Test
    @DisplayName("a valid first answer is used as it stands, with no second call")
    void acceptsAGoodFirstAnswer() {
        SessionDraftGenerator.Attempt attempt = generator(validSession(BENCH))
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isTrue();
        assertThat(attempt.repairUsed()).isFalse();
        assertThat(attempt.firstPassViolations()).isEmpty();
        assertThat(prompts).hasSize(1);
    }

    @Test
    @DisplayName("an invented exercise earns one repair round, and the fix is accepted")
    void repairsAnInventedExercise() {
        SessionDraftGenerator.Attempt attempt = generator(
                validSession(UUID.fromString("99999999-9999-9999-9999-999999999999")),
                validSession(BENCH))
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isTrue();
        assertThat(attempt.repairUsed()).isTrue();
        assertThat(prompts).hasSize(2);
    }

    @Test
    @DisplayName("the repair prompt says what was wrong, not merely that something was")
    void repairPromptCarriesTheViolations() {
        generator(validSession(UUID.fromString("99999999-9999-9999-9999-999999999999")),
                validSession(BENCH))
                .generate("chest", candidates(), context());

        String repairPrompt = prompts.get(1);
        assertThat(repairPrompt).contains("rejected for these reasons");
        assertThat(repairPrompt).contains("99999999-9999-9999-9999-999999999999");
        assertThat(repairPrompt)
                .withFailMessage("a correction the model cannot act on is not worth a round trip")
                .contains("candidate list");
    }

    @Test
    @DisplayName("the first attempt's violations are kept even when the repair fixed them")
    void keepsTheFirstPassViolations() {
        SessionDraftGenerator.Attempt attempt = generator(
                validSession(UUID.fromString("99999999-9999-9999-9999-999999999999")),
                validSession(BENCH))
                .generate("chest", candidates(), context());

        assertThat(attempt.firstPassViolations())
                .withFailMessage("what the model got wrong is the measurement; the version that "
                        + "passed says less")
                .extracting(DraftViolation::code)
                .containsExactly(DraftViolation.Code.UNKNOWN_EXERCISE);
    }

    @Test
    @DisplayName("two failures give up rather than trying a third time")
    void stopsAfterOneRepair() {
        String invalid = validSession(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        SessionDraftGenerator.Attempt attempt = generator(invalid, invalid, validSession(BENCH))
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isFalse();
        assertThat(prompts)
                .withFailMessage("a model wrong twice will not be right on the third call, and the "
                        + "user is waiting")
                .hasSize(2);
    }

    @Test
    @DisplayName("a reply that is not JSON is treated as a failed attempt and repaired once")
    void repairsAnUnparseableReply() {
        SessionDraftGenerator.Attempt attempt = generator(
                "Here is a great chest workout for you!", validSession(BENCH))
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isTrue();
        assertThat(attempt.repairUsed()).isTrue();
    }

    @Test
    @DisplayName("JSON wrapped in prose is still read")
    void toleratesProseAroundTheJson() {
        SessionDraftGenerator.Attempt attempt = generator(
                "Sure! ```json\n" + validSession(BENCH) + "\n``` Enjoy.")
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isTrue();
        assertThat(attempt.repairUsed()).isFalse();
    }

    @Test
    @DisplayName("with the AI tier off nothing is attempted and the caller is told so")
    void reportsAnAbsentModel() {
        SessionDraftGenerator generator = new SessionDraftGenerator(
                emptyProvider(), validator, objectMapper);

        SessionDraftGenerator.Attempt attempt = generator.generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isFalse();
        assertThat(attempt.modelAvailable()).isFalse();
        assertThat(attempt.repairUsed()).isFalse();
    }

    @Test
    @DisplayName("a model that throws is reported as unavailable rather than failing the draft")
    void survivesAModelThatThrows() {
        ClaudeClient throwing = (system, user) -> {
            throw new IllegalStateException("connection refused");
        };
        SessionDraftGenerator generator = new SessionDraftGenerator(
                providerOf(throwing), validator, objectMapper);

        assertThat(generator.generate("chest", candidates(), context()).modelAvailable()).isFalse();
    }

    @Test
    @DisplayName("the first prompt lists the candidate ids the model must choose from")
    void firstPromptCarriesTheCandidates() {
        generator(validSession(BENCH)).generate("chest", candidates(), context());

        assertThat(prompts.get(0)).contains(BENCH.toString());
        assertThat(prompts.get(0)).contains(ROW.toString());
        assertThat(prompts.get(0)).contains("45 minutes");
    }

    @Test
    @DisplayName("a mangled exercise id is reported rather than quietly dropped")
    void doesNotSwallowAMalformedId() {
        String mangled = """
                {"title":"Chest","focus":["CHEST"],"estimatedMinutes":45,"summary":"s",
                 "exercises":[{"exerciseId":"not-a-uuid","name":"Bench","sets":3,"reps":10,
                 "seconds":null,"restSeconds":60,"rationale":"r"}]}
                """;

        SessionDraftGenerator.Attempt attempt = generator(mangled, mangled)
                .generate("chest", candidates(), context());

        assertThat(attempt.succeeded()).isFalse();
        assertThat(attempt.firstPassViolations())
                .extracting(DraftViolation::code)
                .contains(DraftViolation.Code.UNKNOWN_EXERCISE);
    }

    // -------------------------------------------------------------------------------------------

    private SessionDraftGenerator generator(String... scriptedReplies) {
        Deque<String> script = new ArrayDeque<>(List.of(scriptedReplies));
        ClaudeClient scripted = (system, user) -> {
            prompts.add(user);
            return script.isEmpty() ? "" : script.poll();
        };
        return new SessionDraftGenerator(providerOf(scripted), validator, objectMapper);
    }

    private static String validSession(UUID exerciseId) {
        return """
                {"title":"Chest and back","focus":["CHEST"],"estimatedMinutes":45,
                 "summary":"Because you have not trained chest this week.",
                 "exercises":[{"exerciseId":"%s","name":"Barbell Bench Press","sets":3,"reps":10,
                 "seconds":null,"restSeconds":60,"rationale":"Main pressing movement."}]}
                """.formatted(exerciseId);
    }

    private static List<CatalogueExercise> candidates() {
        return List.of(
                new CatalogueExercise(BENCH, "Barbell Bench Press", "CHEST", List.of(), "BARBELL", true),
                new CatalogueExercise(ROW, "Barbell Row", "BACK", List.of(), "BARBELL", true));
    }

    private static DraftContext context() {
        Map<UUID, CatalogueExercise> catalogue = new HashMap<>();
        for (CatalogueExercise candidate : candidates()) {
            catalogue.put(candidate.exerciseId(), candidate);
        }
        return new DraftContext(catalogue, Set.of("BARBELL"), Set.of(), Set.of(), 45);
    }

    private static ObjectProvider<ClaudeClient> providerOf(ClaudeClient client) {
        return new ObjectProvider<>() {
            @Override
            public ClaudeClient getObject(Object... args) {
                return client;
            }

            @Override
            public ClaudeClient getObject() {
                return client;
            }

            @Override
            public ClaudeClient getIfAvailable() {
                return client;
            }

            @Override
            public ClaudeClient getIfUnique() {
                return client;
            }
        };
    }

    private static ObjectProvider<ClaudeClient> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public ClaudeClient getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ClaudeClient getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ClaudeClient getIfAvailable() {
                return null;
            }

            @Override
            public ClaudeClient getIfUnique() {
                return null;
            }
        };
    }
}
