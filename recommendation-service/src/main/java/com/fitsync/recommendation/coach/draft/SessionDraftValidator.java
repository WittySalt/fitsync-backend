package com.fitsync.recommendation.coach.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import org.springframework.stereotype.Component;

/**
 * The gate between what a model proposed and what a user is shown.
 *
 * <p>This is the mechanism the whole design rests on. A language model asked for a workout will
 * produce something that reads correctly whether or not it is correct, and the failure mode is not
 * nonsense but plausibility: an exercise that does not exist, a barbell movement for someone who
 * trains at home, legs the day after legs. None of those look wrong on the page. So the model is
 * never trusted to be right; it is trusted to propose, and these rules decide.
 *
 * <p>Pure, and takes everything it needs as an argument. Every rule is therefore testable without
 * a network, and a repair round is validated against exactly the same facts as the first attempt,
 * so a draft cannot pass the second time because the world moved underneath it.
 *
 * <p>The rules are deliberately conservative where the data is coarse. A user whose equipment list
 * is empty has not told us they own nothing, so the equipment rule stays quiet rather than
 * rejecting everything. That distinction between "no" and "not known" is the difference between a
 * validator that helps and one that makes the feature unusable.
 */
@Component
public class SessionDraftValidator {

    /** Equipment that needs no equipment, so no inventory entry is required for it. */
    private static final Set<String> ALWAYS_AVAILABLE = Set.of("BODYWEIGHT", "OTHER", "");

    private static final int MIN_SETS = 1;
    private static final int MAX_SETS = 10;
    private static final int MAX_REST_SECONDS = 600;

    /**
     * Which muscle groups an injured area rules out.
     *
     * <p>Coarse on purpose, and it errs towards excluding. The mapping is not a clinical judgement
     * and is not offered to the user as one: it is a filter over a catalogue, applied to what the
     * user themselves entered on the readiness questionnaire, so that a knee they told us about
     * does not come back as a squat. Where a mapping is arguable the wider reading wins, because
     * the cost of dropping an exercise someone could have done is one fewer option, and the cost of
     * the other mistake is an injury.
     */
    private static final Map<String, Set<String>> INJURY_EXCLUSIONS = Map.of(
            "KNEE", Set.of("LEGS", "GLUTES"),
            "ANKLE", Set.of("LEGS", "CARDIO"),
            "HIP", Set.of("LEGS", "GLUTES"),
            "LOWER_BACK", Set.of("BACK", "CORE", "LEGS"),
            "BACK", Set.of("BACK", "CORE"),
            "SHOULDER", Set.of("SHOULDERS", "CHEST"),
            "ELBOW", Set.of("ARMS", "CHEST"),
            "WRIST", Set.of("ARMS", "CHEST"),
            "NECK", Set.of("SHOULDERS", "BACK"));

    /** @return every rule broken, in a stable order; empty when the draft is usable */
    public List<DraftViolation> validate(SessionDraft draft, DraftContext context) {
        List<DraftViolation> violations = new ArrayList<>();

        if (draft == null || draft.exercises().isEmpty()) {
            violations.add(DraftViolation.session(DraftViolation.Code.MALFORMED,
                    "A session needs at least one exercise."));
            return violations;
        }

        for (DraftExercise exercise : draft.exercises()) {
            checkStructure(exercise, violations);
            CatalogueExercise row = resolve(exercise, context, violations);
            if (row == null) {
                continue;
            }
            checkActive(exercise, row, violations);
            checkEquipment(exercise, row, context, violations);
            checkRecovery(exercise, row, context, violations);
            checkInjuries(exercise, row, context, violations);
        }

        checkDuration(draft, context, violations);
        return List.copyOf(violations);
    }

    private static void checkStructure(DraftExercise exercise, List<DraftViolation> violations) {
        if (exercise.sets() < MIN_SETS || exercise.sets() > MAX_SETS) {
            violations.add(DraftViolation.of(DraftViolation.Code.MALFORMED, exercise.exerciseId(),
                    "\"%s\" has %d sets. Use between %d and %d."
                            .formatted(exercise.name(), exercise.sets(), MIN_SETS, MAX_SETS)));
        }
        if (exercise.reps() == null && exercise.seconds() == null) {
            violations.add(DraftViolation.of(DraftViolation.Code.MALFORMED, exercise.exerciseId(),
                    "\"%s\" needs either reps or seconds per set.".formatted(exercise.name())));
        }
        if (exercise.restSeconds() < 0 || exercise.restSeconds() > MAX_REST_SECONDS) {
            violations.add(DraftViolation.of(DraftViolation.Code.MALFORMED, exercise.exerciseId(),
                    "\"%s\" rests for %d seconds. Use 0 to %d."
                            .formatted(exercise.name(), exercise.restSeconds(), MAX_REST_SECONDS)));
        }
    }

    /** @return the catalogue row, or null when the identifier did not resolve */
    private static CatalogueExercise resolve(DraftExercise exercise,
                                             DraftContext context,
                                             List<DraftViolation> violations) {
        if (exercise.exerciseId() == null) {
            violations.add(DraftViolation.of(DraftViolation.Code.UNKNOWN_EXERCISE, null,
                    "\"%s\" has no exercise id. Every exercise must use an id from the candidate list."
                            .formatted(exercise.name())));
            return null;
        }
        CatalogueExercise row = context.catalogue().get(exercise.exerciseId());
        if (row == null) {
            violations.add(DraftViolation.of(DraftViolation.Code.UNKNOWN_EXERCISE, exercise.exerciseId(),
                    "There is no exercise with id %s. Replace it with one from the candidate list."
                            .formatted(exercise.exerciseId())));
        }
        return row;
    }

    private static void checkActive(DraftExercise exercise,
                                    CatalogueExercise row,
                                    List<DraftViolation> violations) {
        if (!row.active()) {
            violations.add(DraftViolation.of(DraftViolation.Code.INACTIVE_EXERCISE, exercise.exerciseId(),
                    "\"%s\" has been retired from the catalogue. Choose a different exercise."
                            .formatted(row.name())));
        }
    }

    private static void checkEquipment(DraftExercise exercise,
                                       CatalogueExercise row,
                                       DraftContext context,
                                       List<DraftViolation> violations) {
        // An empty inventory means the user never told us, not that they own nothing. Rejecting
        // every equipment-based exercise for the majority who skipped that question would make the
        // feature useless to them.
        if (context.availableEquipment().isEmpty()) {
            return;
        }
        String equipment = upper(row.equipment());
        if (ALWAYS_AVAILABLE.contains(equipment) || context.availableEquipment().contains(equipment)) {
            return;
        }
        violations.add(DraftViolation.of(DraftViolation.Code.EQUIPMENT_UNAVAILABLE, exercise.exerciseId(),
                "\"%s\" needs %s, which this user does not have. They have: %s."
                        .formatted(row.name(), equipment, String.join(", ", context.availableEquipment()))));
    }

    private static void checkRecovery(DraftExercise exercise,
                                      CatalogueExercise row,
                                      DraftContext context,
                                      List<DraftViolation> violations) {
        // Only the primary group. A secondary group gets worked incidentally by half the catalogue,
        // and treating that as a reason to wait a day would leave almost nothing trainable.
        String primary = upper(row.primaryMuscleGroup());
        if (primary.isEmpty() || !context.recentMuscleGroups().contains(primary)) {
            return;
        }
        violations.add(DraftViolation.of(DraftViolation.Code.MUSCLE_GROUP_NOT_RECOVERED, exercise.exerciseId(),
                "\"%s\" trains %s, which was trained yesterday. Pick a different muscle group."
                        .formatted(row.name(), primary)));
    }

    private static void checkInjuries(DraftExercise exercise,
                                      CatalogueExercise row,
                                      DraftContext context,
                                      List<DraftViolation> violations) {
        for (String area : context.injuryAreas()) {
            Set<String> excluded = INJURY_EXCLUSIONS.get(upper(area));
            if (excluded == null) {
                continue;
            }
            if (excluded.contains(upper(row.primaryMuscleGroup()))) {
                violations.add(DraftViolation.of(DraftViolation.Code.INJURY_EXCLUDED, exercise.exerciseId(),
                        "\"%s\" loads an area this user flagged as injured. Choose something that does not."
                                .formatted(row.name())));
                return;
            }
        }
    }

    private static void checkDuration(SessionDraft draft,
                                      DraftContext context,
                                      List<DraftViolation> violations) {
        int target = context.targetMinutes();
        if (target <= 0) {
            return;
        }
        int lower = (int) Math.floor(target * (1 - DraftContext.DURATION_TOLERANCE));
        int upper = (int) Math.ceil(target * (1 + DraftContext.DURATION_TOLERANCE));
        if (draft.estimatedMinutes() >= lower && draft.estimatedMinutes() <= upper) {
            return;
        }
        violations.add(DraftViolation.session(DraftViolation.Code.DURATION_OUT_OF_RANGE,
                "This session is %d minutes. This user trains for about %d, so aim for %d to %d."
                        .formatted(draft.estimatedMinutes(), target, lower, upper)));
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
