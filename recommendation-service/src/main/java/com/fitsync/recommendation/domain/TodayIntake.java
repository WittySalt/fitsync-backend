package com.fitsync.recommendation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the user has eaten on one local day, as the Nutrition Diary shows it.
 *
 * <p>Read from nutrition-service's daily summary when the facts are assembled, never kept as a
 * running sum here: a sum grows on every logged meal and cannot shrink when one is deleted, which
 * is how the coach once quoted 4910 kcal for a diary that held 476. An unknown intake (no caller
 * token, or nutrition-service unreachable) carries no figures, and the rules then say nothing about
 * what was eaten rather than guess.
 */
public record TodayIntake(LocalDate date, Integer calories, BigDecimal proteinGrams) {

    private static final TodayIntake UNKNOWN = new TodayIntake(null, null, null);

    public static TodayIntake unknown() {
        return UNKNOWN;
    }

    /** True when the diary was read. The caller chose the day, in the zone the user is speaking from. */
    public boolean known() {
        return date != null && calories != null;
    }
}
