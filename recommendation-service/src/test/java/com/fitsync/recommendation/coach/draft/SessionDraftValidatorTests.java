package com.fitsync.recommendation.coach.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The five domain rules, each one on its own.
 *
 * <p>Worth testing exhaustively because this is the component the design's central claim rests on.
 * A language model asked for a workout produces something that reads correctly whether or not it is
 * correct, and the failure mode is plausibility rather than nonsense. If these rules are wrong, the
 * coach ships confident mistakes and nothing downstream notices.
 *
 * <p>The validator takes all its facts as arguments, so every case here runs without a network, a
 * database or a model.
 */
class SessionDraftValidatorTests {

    private static final UUID BENCH = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID SQUAT = UUID.fromString("00000000-0000-0000-0000-00000000005c");
    private static final UUID PLANK = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final SessionDraftValidator validator = new SessionDraftValidator();

    @Test
    @DisplayName("a session drawn from real, allowed exercises passes")
    void acceptsAValidSession() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("an exercise id that resolves to nothing is rejected")
    void rejectsAnInventedExercise() {
        UUID invented = UUID.fromString("99999999-9999-9999-9999-999999999999");
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(invented, "Hyperbolic Chest Fly")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.UNKNOWN_EXERCISE);
        assertThat(violations.get(0).message())
                .withFailMessage("the repair round needs to be told where to choose from")
                .contains("candidate list");
    }

    @Test
    @DisplayName("an exercise retired from the catalogue is rejected")
    void rejectsARetiredExercise() {
        CatalogueExercise retired = new CatalogueExercise(
                BENCH, "Barbell Bench Press", "CHEST", List.of(), "BARBELL", false);
        DraftContext context = new DraftContext(
                Map.of(BENCH, retired), Set.of("BARBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.INACTIVE_EXERCISE);
    }

    @Test
    @DisplayName("equipment the user does not have is rejected")
    void rejectsUnavailableEquipment() {
        DraftContext context = context(Set.of("DUMBBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.EQUIPMENT_UNAVAILABLE);
    }

    @Test
    @DisplayName("an empty equipment list means unknown, not none, so nothing is rejected for it")
    void treatsAnEmptyInventoryAsUnknown() {
        DraftContext context = context(Set.of(), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(violations)
                .withFailMessage("most users skip the equipment question; rejecting everything would "
                        + "make the feature useless to them")
                .isEmpty();
    }

    @Test
    @DisplayName("bodyweight needs no inventory entry")
    void bodyweightIsAlwaysAvailable() {
        DraftContext context = context(Set.of("DUMBBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(PLANK, "Plank Hold")), context);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("a muscle group trained yesterday is rejected")
    void rejectsAMuscleGroupTrainedYesterday() {
        DraftContext context = context(Set.of("BARBELL"), Set.of("CHEST"), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.MUSCLE_GROUP_NOT_RECOVERED);
    }

    @Test
    @DisplayName("an exercise loading a flagged injury is rejected")
    void rejectsAnInjuryExcludedExercise() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of("KNEE"), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(SQUAT, "Barbell Back Squat")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.INJURY_EXCLUDED);
    }

    @Test
    @DisplayName("the injury message does not name the injury back to the model")
    void keepsTheInjuryOutOfTheRepairMessage() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of("KNEE"), 45);

        List<DraftViolation> violations = validator.validate(
                session(45, exercise(SQUAT, "Barbell Back Squat")), context);

        assertThat(violations.get(0).message()).doesNotContain("KNEE");
    }

    @Test
    @DisplayName("a session much longer than the user trains for is rejected")
    void rejectsASessionThatIsTooLong() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(90, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.DURATION_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("a session inside the twenty per cent tolerance is accepted")
    void acceptsASessionInsideTheTolerance() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 45);

        assertThat(validator.validate(session(36, exercise(BENCH, "Barbell Bench Press")), context)).isEmpty();
        assertThat(validator.validate(session(54, exercise(BENCH, "Barbell Bench Press")), context)).isEmpty();
    }

    @Test
    @DisplayName("an exercise with neither reps nor seconds is malformed")
    void rejectsAnExerciseWithNoVolume() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 45);
        DraftExercise noVolume = new DraftExercise(BENCH, "Barbell Bench Press", 3, null, null, 60, "why");

        assertThat(codes(validator.validate(session(45, noVolume), context)))
                .contains(DraftViolation.Code.MALFORMED);
    }

    @Test
    @DisplayName("an empty session is rejected before anything else is checked")
    void rejectsAnEmptySession() {
        List<DraftViolation> violations = validator.validate(
                new SessionDraft("Nothing", List.of(), 45, List.of(), ""),
                context(Set.of(), Set.of(), Set.of(), 45));

        assertThat(codes(violations)).containsExactly(DraftViolation.Code.MALFORMED);
    }

    @Test
    @DisplayName("every broken rule is reported, not just the first")
    void reportsEveryViolation() {
        DraftContext context = context(Set.of("DUMBBELL"), Set.of("CHEST"), Set.of(), 45);

        List<DraftViolation> violations = validator.validate(
                session(120, exercise(BENCH, "Barbell Bench Press")), context);

        assertThat(codes(violations)).containsExactlyInAnyOrder(
                DraftViolation.Code.EQUIPMENT_UNAVAILABLE,
                DraftViolation.Code.MUSCLE_GROUP_NOT_RECOVERED,
                DraftViolation.Code.DURATION_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("with no history to go on, session length is not judged")
    void skipsTheDurationRuleWithoutATarget() {
        DraftContext context = context(Set.of("BARBELL"), Set.of(), Set.of(), 0);

        assertThat(validator.validate(session(150, exercise(BENCH, "Barbell Bench Press")), context))
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------------

    private static List<DraftViolation.Code> codes(List<DraftViolation> violations) {
        return violations.stream().map(DraftViolation::code).toList();
    }

    private static SessionDraft session(int minutes, DraftExercise... exercises) {
        return new SessionDraft("Session", List.of("CHEST"), minutes, List.of(exercises), "summary");
    }

    private static DraftExercise exercise(UUID exerciseId, String name) {
        return new DraftExercise(exerciseId, name, 3, 10, null, 60, "because");
    }

    private static DraftContext context(Set<String> equipment,
                                        Set<String> recentGroups,
                                        Set<String> injuries,
                                        int targetMinutes) {
        return new DraftContext(catalogue(), equipment, recentGroups, injuries, targetMinutes);
    }

    private static Map<UUID, CatalogueExercise> catalogue() {
        return Map.of(
                BENCH, new CatalogueExercise(BENCH, "Barbell Bench Press", "CHEST", List.of(), "BARBELL", true),
                SQUAT, new CatalogueExercise(SQUAT, "Barbell Back Squat", "LEGS", List.of(), "BARBELL", true),
                PLANK, new CatalogueExercise(PLANK, "Plank Hold", "CORE", List.of(), "BODYWEIGHT", true));
    }
}
