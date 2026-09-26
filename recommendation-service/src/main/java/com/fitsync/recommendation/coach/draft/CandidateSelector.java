package com.fitsync.recommendation.coach.draft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import org.springframework.stereotype.Component;

/**
 * Narrows a candidate pool to what a session could legitimately be built from.
 *
 * <p>The same structured-filter-first argument the exercise search rests on, applied one layer up.
 * A rule the catalogue can settle should be settled before anything is asked to choose: the model
 * then picks among options that are all allowed, rather than being told in prose to avoid things
 * that make up most of the list it was handed.
 *
 * <p>That is not a hypothetical. The first working version showed the model twenty-four candidates
 * ordered by requested muscle group, and after a full-body session the day before, almost all of
 * them broke the recovery rule. The prompt did say to avoid those groups. The model picked them
 * anyway, every time, because there was very little else in front of it. Filtering first fixed what
 * more prompt wording did not.
 *
 * <p>The validator still checks everything afterwards. This narrows what is offered; it decides
 * nothing, and a draft that reaches past this list is still judged on its own terms.
 */
@Component
public class CandidateSelector {

    /** How many movements one muscle group may contribute before the next group gets a turn. */
    private static final int MAX_PER_GROUP = 3;

    private final SessionDraftValidator validator;

    public CandidateSelector(SessionDraftValidator validator) {
        this.validator = validator;
    }

    /**
     * Candidates that break no rule on their own, ordered so muscle groups alternate.
     *
     * <p>Round robin rather than one per group. A user asking for chest and back has two groups to
     * draw from, and a cap of one each leaves two exercises where a session needs at least three,
     * which is how the first version of the rule fallback managed to refuse every narrow request.
     */
    public List<CatalogueExercise> eligible(List<CatalogueExercise> pool, DraftContext context) {
        Map<String, List<CatalogueExercise>> byGroup = new LinkedHashMap<>();
        for (CatalogueExercise row : sorted(pool)) {
            if (!row.active() || breaksARule(row, context)) {
                continue;
            }
            byGroup.computeIfAbsent(upper(row.primaryMuscleGroup()), key -> new ArrayList<>()).add(row);
        }

        List<CatalogueExercise> eligible = new ArrayList<>();
        for (int round = 0; round < MAX_PER_GROUP; round++) {
            for (List<CatalogueExercise> group : byGroup.values()) {
                if (round < group.size()) {
                    eligible.add(group.get(round));
                }
            }
        }
        return List.copyOf(eligible);
    }

    /**
     * Whether one exercise, on its own, already fails validation.
     *
     * <p>Asked by running the validator over a one-exercise session rather than by restating its
     * rules here. A second copy of the equipment table and the injury mapping would be one edit away
     * from disagreeing with the first, and the disagreement would show up as a draft that passes the
     * filter and then fails the gate, which is the confusing direction for it to fail in.
     */
    private boolean breaksARule(CatalogueExercise row, DraftContext context) {
        DraftExercise probe = new DraftExercise(row.exerciseId(), row.name(), 3, 10, null, 60, "probe");
        // Target minutes zeroed: a single exercise is not a session, and judging its length here
        // would reject every candidate.
        DraftContext probeContext = new DraftContext(
                Map.of(row.exerciseId(), row),
                context.availableEquipment(),
                context.recentMuscleGroups(),
                context.injuryAreas(),
                0);
        return !validator.validate(new SessionDraft("probe", List.of(), 0, List.of(probe), ""), probeContext)
                .isEmpty();
    }

    /** Stable order, so the same pool always produces the same list. */
    private static List<CatalogueExercise> sorted(List<CatalogueExercise> pool) {
        List<CatalogueExercise> copy = new ArrayList<>(pool == null ? List.of() : pool);
        copy.sort(Comparator.comparing(row -> String.valueOf(row.name()), String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
