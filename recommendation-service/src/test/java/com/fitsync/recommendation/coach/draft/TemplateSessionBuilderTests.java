package com.fitsync.recommendation.coach.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The path taken when the model fails twice or is switched off entirely.
 *
 * <p>The central assertion is the same in almost every case here: whatever the builder returns must
 * pass the validator. A fallback that produced an invalid session would be worse than no fallback,
 * because it runs precisely when nothing else is left to catch it.
 */
class TemplateSessionBuilderTests {

    private final SessionDraftValidator validator = new SessionDraftValidator();
    private final TemplateSessionBuilder builder = new TemplateSessionBuilder(validator, new CandidateSelector(validator));

    @Test
    @DisplayName("a session built from the catalogue passes the same validator the model answers to")
    void buildsAValidSession() {
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 45);

        Optional<SessionDraft> draft = builder.build(candidates(), context);

        assertThat(draft).isPresent();
        assertThat(validator.validate(draft.orElseThrow(), context)).isEmpty();
    }

    @Test
    @DisplayName("it spreads across muscle groups rather than stacking one")
    void spreadsAcrossMuscleGroups() {
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 45);
        SessionDraft draft = builder.build(candidates(), context).orElseThrow();

        assertThat(draft.focus()).doesNotHaveDuplicates();

        Map<String, Long> perGroup = draft.exercises().stream().collect(java.util.stream.Collectors
                .groupingBy(exercise -> context.catalogue().get(exercise.exerciseId()).primaryMuscleGroup(),
                        java.util.stream.Collectors.counting()));
        assertThat(perGroup.values())
                .withFailMessage("a template session must not be five variations of one movement")
                .allMatch(count -> count <= 3);
        assertThat(perGroup.keySet()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("a request narrowed to two muscle groups still yields a full session")
    void buildsFromANarrowRequest() {
        // The first version capped this at one exercise per group, so chest and back produced two
        // movements where the minimum is three and the fallback refused to build at all. A live run
        // found it immediately.
        List<CatalogueExercise> narrow = candidates().stream()
                .filter(candidate -> Set.of("CHEST", "BACK").contains(candidate.primaryMuscleGroup()))
                .toList();
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 45);

        SessionDraft draft = builder.build(narrow, context).orElseThrow();

        assertThat(draft.exercises()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(validator.validate(draft, context)).isEmpty();
    }

    @Test
    @DisplayName("it skips equipment the user does not have")
    void respectsTheEquipmentInventory() {
        DraftContext context = context(Set.of("BODYWEIGHT"), Set.of(), Set.of(), 45);

        SessionDraft draft = builder.build(candidates(), context).orElseThrow();

        assertThat(validator.validate(draft, context)).isEmpty();
        assertThat(draft.exercises()).allSatisfy(exercise ->
                assertThat(context.catalogue().get(exercise.exerciseId()).equipment())
                        .isEqualTo("BODYWEIGHT"));
    }

    @Test
    @DisplayName("it skips what was trained yesterday")
    void respectsRecovery() {
        DraftContext context = context(Set.of(), Set.of("CHEST", "LEGS"), Set.of(), 45);

        SessionDraft draft = builder.build(candidates(), context).orElseThrow();

        assertThat(draft.focus()).doesNotContain("CHEST", "LEGS");
        assertThat(validator.validate(draft, context)).isEmpty();
    }

    @Test
    @DisplayName("it skips what a flagged injury rules out")
    void respectsInjuries() {
        DraftContext context = context(Set.of(), Set.of(), Set.of("KNEE"), 45);

        SessionDraft draft = builder.build(candidates(), context).orElseThrow();

        assertThat(draft.focus()).doesNotContain("LEGS", "GLUTES");
        assertThat(validator.validate(draft, context)).isEmpty();
    }

    @Test
    @DisplayName("a session for someone who trains briefly is still inside the duration rule")
    void fitsAShortSession() {
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 25);

        SessionDraft draft = builder.build(candidates(), context).orElseThrow();

        assertThat(validator.validate(draft, context)).isEmpty();
    }

    @Test
    @DisplayName("too few usable exercises produces nothing rather than a two-move session")
    void refusesToBuildFromTooLittle() {
        List<CatalogueExercise> thin = candidates().subList(0, 2);

        assertThat(builder.build(thin, context(Set.of(), Set.of(), Set.of(), 45))).isEmpty();
    }

    @Test
    @DisplayName("the same candidates always build the same session")
    void isDeterministic() {
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 45);

        SessionDraft first = builder.build(candidates(), context).orElseThrow();
        SessionDraft second = builder.build(shuffled(), context).orElseThrow();

        assertThat(first.exercises()).isEqualTo(second.exercises());
    }

    @Test
    @DisplayName("it says plainly that the coach did not write this one")
    void admitsWhereItCameFrom() {
        SessionDraft draft = builder.build(candidates(), context(Set.of(), Set.of(), Set.of(), 45))
                .orElseThrow();

        assertThat(draft.summary()).contains("catalogue");
        assertThat(draft.exercises()).allSatisfy(exercise ->
                assertThat(exercise.rationale()).isNotBlank());
    }

    // -------------------------------------------------------------------------------------------

    private DraftContext context(Set<String> equipment,
                                 Set<String> recentGroups,
                                 Set<String> injuries,
                                 int targetMinutes) {
        Map<UUID, CatalogueExercise> catalogue = new java.util.HashMap<>();
        for (CatalogueExercise candidate : candidates()) {
            catalogue.put(candidate.exerciseId(), candidate);
        }
        return new DraftContext(catalogue, equipment, recentGroups, injuries, targetMinutes);
    }

    /** One per muscle group plus a bodyweight alternative, which is enough to exercise every path. */
    private static List<CatalogueExercise> candidates() {
        return List.of(
                candidate("Barbell Bench Press", "CHEST", "BARBELL"),
                candidate("Barbell Row", "BACK", "BARBELL"),
                candidate("Barbell Back Squat", "LEGS", "BARBELL"),
                candidate("Overhead Press", "SHOULDERS", "BARBELL"),
                candidate("Hip Thrust", "GLUTES", "BARBELL"),
                candidate("Push-Up", "CHEST", "BODYWEIGHT"),
                candidate("Pull-Up", "BACK", "BODYWEIGHT"),
                candidate("Air Squat", "LEGS", "BODYWEIGHT"),
                candidate("Pike Push-Up", "SHOULDERS", "BODYWEIGHT"),
                candidate("Plank Hold", "CORE", "BODYWEIGHT"));
    }

    private static List<CatalogueExercise> shuffled() {
        List<CatalogueExercise> copy = new ArrayList<>(candidates());
        java.util.Collections.reverse(copy);
        return copy;
    }

    /** A stable identifier per name, so a reordered list is the same set of exercises. */
    private static CatalogueExercise candidate(String name, String group, String equipment) {
        return new CatalogueExercise(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name, group, List.of(), equipment, true);
    }
}
