package com.fitsync.recommendation.service.rules;

/**
 * The recommendation types produced by the Tier-1 rules (README §4.1). These strings
 * are the {@code recommendation_result.type} values and the read-API type keys.
 */
public final class RecommendationTypes {

    public static final String NUTRITION_GAP = "NUTRITION_GAP";
    public static final String WEEKLY_ADHERENCE = "WEEKLY_ADHERENCE";
    public static final String GOAL_PACE = "GOAL_PACE";
    /**
     * Retired: no rule produces it any more. The streak has one definition, the analytics
     * ledger the Home page shows, and the coach reads it live from there. Kept so stored
     * rows and the admin console still name it.
     */
    public static final String STREAK_NUDGE = "STREAK_NUDGE";
    public static final String COLD_START = "COLD_START";
    public static final String PERSONAL_RECORD = "PERSONAL_RECORD";
    public static final String LOGGING_REMINDER = "LOGGING_REMINDER";

    /** Tier-2 (A1): the AI-phrased weekly coaching narrative — source AI, not a rule. */
    public static final String WEEKLY_NARRATIVE = "WEEKLY_NARRATIVE";

    private RecommendationTypes() {
    }
}
