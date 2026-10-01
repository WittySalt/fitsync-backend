package com.fitsync.nutrition.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.DrinkWaterEntryView;
import com.fitsync.nutrition.dto.NutrientSummaryView;
import com.fitsync.nutrition.dto.RangeSummaryView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;

/**
 * The read side of the redesigned Diary: the day summary with its balance and protein spread, the drinks that
 * count toward water, a run of days for the week strip, and the vitamin and mineral averages. Every method reads
 * only the rows of the person it is called for.
 */
public interface NutritionDiaryService {

    DailyNutritionSummaryView getDailySummary(CurrentUserPrincipal principal, LocalDate date, String timezone);

    /** The drinks logged in {@code [from, to)} that carry a water amount, oldest first. */
    List<DrinkWaterEntryView> listDrinkWater(UUID userId, Instant from, Instant to);

    /** One entry per day from {@code from} to {@code to}, both included, at most 31 days. */
    RangeSummaryView getRangeSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to, String timezone);

    /** Vitamin and mineral averages over the logged days from {@code from} to {@code to}, at most 31 days. */
    NutrientSummaryView getNutrientSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to,
                                           String timezone);
}
