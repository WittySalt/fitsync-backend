package com.fitsync.recommendation.coach.draft;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the validator needs about one user, gathered once before validation runs.
 *
 * <p>Passed in rather than fetched inside the validator so that validation is a pure function of
 * its inputs. That matters twice over. It lets every rule be tested without a network or a
 * database, which is the difference between five rules that are covered and five that are asserted
 * to work. And it means a repair round validates against exactly the same facts as the first
 * attempt, so a draft cannot pass the second time because the world moved underneath it.
 *
 * @param catalogue          the exercises referenced by the draft, resolved; a missing key is an
 *                           identifier that did not resolve
 * @param availableEquipment what the user has said they can train with. Empty means unknown rather
 *                           than none, and the rule treats it that way
 * @param recentMuscleGroups muscle groups trained the day before, which is what recovery turns on
 * @param injuryAreas        areas the user flagged on the readiness questionnaire, uppercased
 * @param targetMinutes      the session length this user's history suggests
 */
public record DraftContext(
        Map<UUID, CatalogueExercise> catalogue,
        Set<String> availableEquipment,
        Set<String> recentMuscleGroups,
        Set<String> injuryAreas,
        int targetMinutes) {

    /**
     * The tolerance on session length. Twenty per cent of a forty-five minute session is nine
     * minutes, which is about the granularity a session can honestly be planned to.
     */
    public static final double DURATION_TOLERANCE = 0.20;

    public DraftContext {
        catalogue = catalogue == null ? Map.of() : Map.copyOf(catalogue);
        availableEquipment = availableEquipment == null ? Set.of() : Set.copyOf(availableEquipment);
        recentMuscleGroups = recentMuscleGroups == null ? Set.of() : Set.copyOf(recentMuscleGroups);
        injuryAreas = injuryAreas == null ? Set.of() : Set.copyOf(injuryAreas);
    }

    @Override
    public Map<UUID, CatalogueExercise> catalogue() {
        return Map.copyOf(catalogue);
    }

    @Override
    public Set<String> availableEquipment() {
        return Set.copyOf(availableEquipment);
    }

    @Override
    public Set<String> recentMuscleGroups() {
        return Set.copyOf(recentMuscleGroups);
    }

    @Override
    public Set<String> injuryAreas() {
        return Set.copyOf(injuryAreas);
    }

    /**
     * A catalogue row, narrowed to what a rule turns on.
     *
     * @param active    false once an exercise has been retired; a draft may still reference one
     * @param equipment BODYWEIGHT and OTHER are treated as always available by the equipment rule
     */
    public record CatalogueExercise(
            UUID exerciseId,
            String name,
            String primaryMuscleGroup,
            List<String> secondaryMuscleGroups,
            String equipment,
            boolean active) {

        public CatalogueExercise {
            secondaryMuscleGroups = secondaryMuscleGroups == null
                    ? List.of()
                    : List.copyOf(secondaryMuscleGroups);
        }

        @Override
        public List<String> secondaryMuscleGroups() {
            return List.copyOf(secondaryMuscleGroups);
        }
    }
}
