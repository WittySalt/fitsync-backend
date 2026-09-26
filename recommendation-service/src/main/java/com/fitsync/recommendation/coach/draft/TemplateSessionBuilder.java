package com.fitsync.recommendation.coach.draft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import org.springframework.stereotype.Component;

/**
 * Builds a session from the catalogue without asking a model anything.
 *
 * <p>This is what the feature degrades to, and it exists for two separate reasons that happen to
 * have the same answer. A model that fails validation twice has had its chance, and returning
 * nothing after the user waited is the worst available outcome. And the AI tier can be switched off
 * entirely, which is a supported configuration rather than a fault, so there has to be a path that
 * never involves inference at all.
 *
 * <p>It composes rather than validates. Candidates are filtered against the same facts the
 * validator checks, one exercise per muscle group so the session is not four variations of a bench
 * press, and the set and rest numbers come from the session length rather than from taste. A draft
 * built this way passes validation by construction, which is asserted by a test rather than
 * assumed.
 *
 * <p>The result is plainer than a good model's. It is also never wrong, and it is honest about
 * where it came from: the artifact records RULE_FALLBACK, and the user is told the coach built this
 * one from their catalogue.
 */
@Component
public class TemplateSessionBuilder {

    /** Roughly how long one working set plus its rest takes, used to fit the session to time. */
    private static final int SECONDS_PER_SET = 45;
    private static final int DEFAULT_REST_SECONDS = 75;
    private static final int SETS_PER_EXERCISE = 3;
    private static final int DEFAULT_REPS = 10;
    private static final int MIN_EXERCISES = 3;
    private static final int MAX_EXERCISES = 7;

    private final SessionDraftValidator validator;
    private final CandidateSelector selector;

    public TemplateSessionBuilder(SessionDraftValidator validator, CandidateSelector selector) {
        this.validator = validator;
        this.selector = selector;
    }

    /**
     * @param candidates the same pool the model was offered, so the fallback is not quietly working
     *                   from a different catalogue than the attempt it replaces
     * @return a session that passes validation, or empty when the candidate pool cannot produce one
     */
    public java.util.Optional<SessionDraft> build(List<CatalogueExercise> candidates, DraftContext context) {
        List<CatalogueExercise> usable = selector.eligible(candidates, context);
        if (usable.size() < MIN_EXERCISES) {
            return java.util.Optional.empty();
        }

        int minutes = context.targetMinutes() > 0 ? context.targetMinutes() : 45;
        int affordable = (minutes * 60) / (SETS_PER_EXERCISE * (SECONDS_PER_SET + DEFAULT_REST_SECONDS));
        int count = Math.max(MIN_EXERCISES, Math.min(affordable, Math.min(MAX_EXERCISES, usable.size())));

        List<DraftExercise> exercises = new ArrayList<>();
        Set<String> focus = new LinkedHashSet<>();
        for (CatalogueExercise row : usable.subList(0, count)) {
            focus.add(upper(row.primaryMuscleGroup()));
            exercises.add(new DraftExercise(
                    row.exerciseId(),
                    row.name(),
                    SETS_PER_EXERCISE,
                    DEFAULT_REPS,
                    null,
                    DEFAULT_REST_SECONDS,
                    "Covers %s, and fits the equipment you train with."
                            .formatted(lower(row.primaryMuscleGroup()))));
        }

        int estimated = (count * SETS_PER_EXERCISE * (SECONDS_PER_SET + DEFAULT_REST_SECONDS)) / 60;
        SessionDraft draft = new SessionDraft(
                "%s session".formatted(title(focus)),
                List.copyOf(focus),
                // Held to the user's usual length rather than to the arithmetic, which the rounding
                // above can push just outside the tolerance the validator allows.
                clampToTolerance(estimated, context.targetMinutes()),
                exercises,
                "Built from your exercise catalogue rather than written by the coach's model, so it "
                        + "is plainer than usual. Every movement fits your equipment and avoids what "
                        + "you trained yesterday.");

        // Built to pass, but asserted rather than assumed: a fallback that is itself invalid would
        // be worse than no fallback, because nothing downstream would catch it.
        return validator.validate(draft, context).isEmpty()
                ? java.util.Optional.of(draft)
                : java.util.Optional.empty();
    }





    private static int clampToTolerance(int estimated, int target) {
        if (target <= 0) {
            return estimated;
        }
        int lower = (int) Math.floor(target * (1 - DraftContext.DURATION_TOLERANCE));
        int upper = (int) Math.ceil(target * (1 + DraftContext.DURATION_TOLERANCE));
        return Math.max(lower, Math.min(estimated, upper));
    }

    private static String title(Set<String> focus) {
        if (focus.isEmpty()) {
            return "Full body";
        }
        return focus.stream().map(TemplateSessionBuilder::titleCase).reduce((a, b) -> a + " and " + b)
                .orElse("Full body");
    }

    private static String titleCase(String value) {
        String lower = lower(value);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
