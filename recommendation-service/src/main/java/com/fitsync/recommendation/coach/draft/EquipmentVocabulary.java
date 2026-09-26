package com.fitsync.recommendation.coach.draft;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates what a user said they own into what the exercise catalogue calls it.
 *
 * <p>Two bounded contexts, two vocabularies, and neither is wrong. user-service records the
 * onboarding answer as a code from its own reference list, where "full gym" is one choice among
 * eight. workout-service classifies an exercise by the single piece of equipment it needs. Nothing
 * in either service is going to reconcile that, so the translation lives here, where the two are
 * being compared.
 *
 * <p>Found by running the thing rather than by testing it. Every unit test passed while the live
 * pipeline could not build a session at all, because the user's stored inventory read
 * {@code 230002,230003,230008} and the validator was comparing it against names like BARBELL. That
 * is the standing hazard with cross-context integration: both sides are internally consistent and
 * the seam between them is where the defect lives.
 *
 * <p>The mapping widens rather than narrows. "Full gym" opens everything, and machines cover cables
 * too, because refusing an exercise someone could do costs them one option while permitting one
 * they cannot do wastes their session.
 */
final class EquipmentVocabulary {

    /** A pull-up bar maps to bodyweight, which the validator already treats as always available. */
    private static final Map<String, Set<String>> BY_CODE = Map.of(
            "230001", Set.of("BODYWEIGHT"),
            "230002", Set.of("DUMBBELL"),
            "230003", Set.of("BARBELL"),
            "230004", Set.of("BANDS"),
            "230005", Set.of("BODYWEIGHT"),
            "230006", Set.of("KETTLEBELL"),
            "230007", Set.of("MACHINE", "CABLE"),
            "230008", Set.of("BARBELL", "DUMBBELL", "CABLE", "MACHINE", "KETTLEBELL", "BANDS", "OTHER"));

    /**
     * Also accepted by name, because the same field is written by more than one path.
     *
     * <p>A user onboarding through the questionnaire stores codes; a seeded or imported profile can
     * hold the catalogue's own names. Reading both means neither path silently produces a user for
     * whom no session can be drafted.
     */
    private static final Set<String> KNOWN_NAMES = Set.of(
            "BARBELL", "DUMBBELL", "CABLE", "MACHINE", "BODYWEIGHT", "KETTLEBELL", "BANDS", "OTHER");

    private EquipmentVocabulary() {
    }

    /**
     * @param stored the comma-separated inventory as the projection holds it
     * @return catalogue equipment names, or empty when nothing was stored, which the validator reads
     *         as "not known" rather than "owns nothing"
     */
    static Set<String> toCatalogueEquipment(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        Set<String> translated = new LinkedHashSet<>();
        for (String part : stored.split(",")) {
            String token = part.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (token.isEmpty()) {
                continue;
            }
            Set<String> byCode = BY_CODE.get(token);
            if (byCode != null) {
                translated.addAll(byCode);
            } else if (KNOWN_NAMES.contains(token)) {
                translated.add(token);
            }
            // Anything else is a code this service does not know. Dropped rather than passed
            // through, because an unrecognised token can only ever fail to match and would quietly
            // narrow what the user is allowed to train with.
        }
        return translated;
    }
}
