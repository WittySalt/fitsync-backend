package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fitsync.analytics.dto.ProgressHighlight;
import org.junit.jupiter.api.Test;

class ProgressHighlightRulesTests {

    private static final LocalDate MONDAY = LocalDate.parse("2026-08-10");

    @Test
    void rateInRangeFiresInsideTheBandOnlyWhenLosingTowardTheGoal() {
        ProgressHighlight fired = ProgressHighlightRules.rateInRange(new BigDecimal("-0.41"), new BigDecimal("-0.28"),
                new BigDecimal("71.2"), new BigDecimal("68.0"));
        assertThat(fired.code()).isEqualTo("RATE_IN_RANGE");
        assertThat(fired.title()).isEqualTo("You lose 0.41 kg a week, inside the 0.25 to 0.5 kg range that keeps muscle.");
        assertThat(fired.context()).startsWith("Was 0.28 kg a week");
        assertThat(fired.series()).extracting(ProgressHighlight.Point::value).containsExactly(0.41, 0.25, 0.5);

        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("-0.25"), null, null, null)).isNotNull();
        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("-0.50"), null, null, null)).isNotNull();
        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("-0.24"), null, null, null)).isNull();
        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("-0.51"), null, null, null)).isNull();
        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("0.30"), null, null, null)).isNull();
        assertThat(ProgressHighlightRules.rateInRange(null, null, null, null)).isNull();
        // Losing while the goal is to gain is not a good pace.
        assertThat(ProgressHighlightRules.rateInRange(new BigDecimal("-0.30"), null, new BigDecimal("60.0"),
                new BigDecimal("65.0"))).isNull();
    }

    @Test
    void logVsScaleFiresWithBothFigures() {
        ProgressHighlight fired = ProgressHighlightRules.logVsScale(MONDAY, MONDAY.plusWeeks(7), 8, -0.7, -2.7);
        assertThat(fired.title()).isEqualTo("The log says −0.7 kg, the scale says −2.7 kg over 8 weeks.");
        assertThat(fired.context()).isEqualTo("From 10 Aug to 4 Oct. Days without a food log are not counted.");
        assertThat(ProgressHighlightRules.logVsScale(MONDAY, MONDAY, 1, null, -2.7)).isNull();
        assertThat(ProgressHighlightRules.logVsScale(MONDAY, MONDAY, 1, -0.7, null)).isNull();
    }

    @Test
    void waistVsWeightFiresWhenTheWaistCameDownHalfACentimetre() {
        List<LocalDate> dates = List.of(LocalDate.parse("2026-09-05"), LocalDate.parse("2026-10-03"));
        ProgressHighlight fired = ProgressHighlightRules.waistVsWeight(dates,
                List.of(new BigDecimal("85.9"), new BigDecimal("83.8")), new BigDecimal("-1.63"));
        assertThat(fired.title()).isEqualTo("Waist is down 2.1 cm while weight is down 1.6 kg.");
        assertThat(fired.series()).hasSize(2);

        assertThat(ProgressHighlightRules.waistVsWeight(dates, List.of(new BigDecimal("85.9"), new BigDecimal("85.5")),
                new BigDecimal("-1.6"))).isNull();
        assertThat(ProgressHighlightRules.waistVsWeight(dates.subList(0, 1), List.of(new BigDecimal("85.9")),
                new BigDecimal("-1.6"))).isNull();
        assertThat(ProgressHighlightRules.waistVsWeight(dates, List.of(new BigDecimal("85.9"), new BigDecimal("83.8")),
                null)).isNull();
        assertThat(ProgressHighlightRules.waistVsWeight(dates, List.of(new BigDecimal("85.9"), new BigDecimal("83.8")),
                new BigDecimal("0.4")).title()).endsWith("while weight is up 0.4 kg.");
    }

    @Test
    void proteinMetFiresWithATargetAndThreeLoggedDays() {
        List<LocalDate> dates = List.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2));
        List<BigDecimal> grams = List.of(new BigDecimal("150"), new BigDecimal("120"), new BigDecimal("141"));
        ProgressHighlight fired = ProgressHighlightRules.proteinMet(dates, grams, 2, new BigDecimal("140"));
        assertThat(fired.title()).isEqualTo("Protein was met on 2 of 3 logged days.");
        assertThat(fired.inputs()).containsEntry("metDays", 2).containsEntry("loggedDays", 3);

        assertThat(ProgressHighlightRules.proteinMet(dates, grams, 2, null)).isNull();
        assertThat(ProgressHighlightRules.proteinMet(dates.subList(0, 2), grams.subList(0, 2), 1,
                new BigDecimal("140"))).isNull();
    }

    @Test
    void proteinDriftFiresFromAnEightPercentMove() {
        List<LocalDate> weeks = List.of(MONDAY, MONDAY.plusWeeks(7));
        ProgressHighlight fired = ProgressHighlightRules.proteinDrift(weeks,
                List.of(new BigDecimal("150.4"), new BigDecimal("125.6")), new BigDecimal("140"));
        assertThat(fired.title()).isEqualTo("Protein drifted from 150 g to 126 g a day.");
        assertThat(fired.context()).isEqualTo("−25 g a day since the week of 10 Aug. The target is 140 g.");

        assertThat(ProgressHighlightRules.proteinDrift(weeks, List.of(new BigDecimal("150"), new BigDecimal("139")),
                null)).isNull();
        assertThat(ProgressHighlightRules.proteinDrift(weeks.subList(0, 1), List.of(new BigDecimal("150")), null))
                .isNull();
        assertThat(ProgressHighlightRules.proteinDrift(weeks, List.of(new BigDecimal("100"), new BigDecimal("108")),
                null)).isNotNull();
    }

    @Test
    void plateShiftFiresFromThreePoints() {
        ProgressHighlight fired = ProgressHighlightRules.plateShift("VEG", new BigDecimal("4.9"), new BigDecimal("12.0"));
        assertThat(fired.title()).isEqualTo("Vegetables are a bigger share of your plate than in the period before.");
        assertThat(ProgressHighlightRules.plateShift("DAIRY", new BigDecimal("15.0"), new BigDecimal("11.0")).title())
                .isEqualTo("Dairy is a smaller share of your plate than in the period before.");
        assertThat(ProgressHighlightRules.plateShift("VEG", new BigDecimal("10.0"), new BigDecimal("12.9"))).isNull();
        assertThat(ProgressHighlightRules.plateShift(null, null, null)).isNull();
    }

    @Test
    void largestMealFiresFrom35Percent() {
        ProgressHighlight fired = ProgressHighlightRules.largestMeal("DINNER", 38, 807, 2123, List.of());
        assertThat(fired.title()).isEqualTo("Dinner is your largest meal, 38% of the day.");
        assertThat(fired.context()).isEqualTo("807 of 2,123 kcal a day on logged days");
        assertThat(ProgressHighlightRules.largestMeal("DINNER", 34, 700, 2100, List.of())).isNull();
    }
}
