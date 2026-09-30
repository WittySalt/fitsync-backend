package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NutritionFactsBuilderTests {

    private final NutritionFactsBuilder builder = new NutritionFactsBuilder();

    @Test
    @DisplayName("a full week gives the four facts in order, formatted as the page shows them")
    void fullWeekGivesFourFacts() {
        List<ReadingFact> facts = builder.build(PageInsightFixtures.nutrition());

        assertThat(facts).extracting(ReadingFact::key).containsExactly(
                "PROTEIN_PER_DAY", "PROTEIN_BREAKFAST", "PROTEIN_LUNCH_DINNER", "CALORIES_PER_DAY");
        assertThat(facts).extracting(ReadingFact::number).containsExactly(1, 2, 3, 4);

        ReadingFact protein = facts.get(0);
        assertThat(protein.label()).isEqualTo("Protein a day");
        assertThat(protein.value()).isEqualTo("163.8");
        assertThat(protein.unit()).isEqualTo("g");
        assertThat(protein.note()).isEqualTo("Up 1%");
        assertThat(protein.detail()).isEqualTo("162.2 g the week before; target 140 g");

        ReadingFact breakfast = facts.get(1);
        assertThat(breakfast.label()).isEqualTo("Protein at breakfast");
        assertThat(breakfast.value()).isEqualTo("30.3");
        assertThat(breakfast.unit()).isEqualTo("g");
        assertThat(breakfast.note()).isEqualTo("18% of the day");
        assertThat(breakfast.detail()).isEmpty();

        ReadingFact lunchDinner = facts.get(2);
        assertThat(lunchDinner.label()).isEqualTo("Protein at lunch and dinner");
        assertThat(lunchDinner.value()).isEqualTo("109.9");
        assertThat(lunchDinner.note()).isEqualTo("67% of the day");

        ReadingFact calories = facts.get(3);
        assertThat(calories.label()).isEqualTo("Calories a day");
        assertThat(calories.value()).isEqualTo("2,173");
        assertThat(calories.unit()).isEqualTo("kcal");
        assertThat(calories.note()).isEqualTo("Up 1%");
        assertThat(calories.detail()).isEqualTo("2,153 kcal the week before; target 2,100 kcal");
    }

    @Test
    @DisplayName("a whole number of grams drops its trailing zero, and a fall is written Down")
    void wholeGramsAndAFall() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("150.00"), new BigDecimal("156.0"),
                new BigDecimal("2000"), new BigDecimal("2000"), List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts.get(0).value()).isEqualTo("150");
        assertThat(facts.get(0).note()).isEqualTo("Down 4%");
        assertThat(facts.get(0).detail()).isEqualTo("156 g the week before; target 140 g");
        assertThat(facts.get(1).key()).isEqualTo("CALORIES_PER_DAY");
        assertThat(facts.get(1).note()).isEqualTo("Unchanged");
    }

    @Test
    @DisplayName("without an earlier week the note is empty, and the detail is only the target")
    void noEarlierWeek() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("150"), null,
                new BigDecimal("2000"), null, List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts.get(0).note()).isEmpty();
        assertThat(facts.get(0).detail()).isEqualTo("target 140 g");
        assertThat(facts.get(1).note()).isEmpty();
        assertThat(facts.get(1).detail()).isEqualTo("target 2,100 kcal");
    }

    @Test
    @DisplayName("without a target the detail is only the earlier week, and empty when there is neither")
    void noTarget() {
        NutritionInsight insight = new NutritionInsight("UTC", "PREVIOUS_WEEK", null, null, 7,
                new BigDecimal("150"), new BigDecimal("140"), null,
                new BigDecimal("2000"), null, null, List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts.get(0).detail()).isEqualTo("140 g the week before");
        assertThat(facts.get(1).detail()).isEmpty();
    }

    @Test
    @DisplayName("a fact is left out when its number is null, and the rest are renumbered")
    void nullNumbersLeaveTheFactOut() {
        NutritionInsight insight = new NutritionInsight("UTC", "PREVIOUS_WEEK", null, null, 7,
                null, null, null, new BigDecimal("2173"), null, null, List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).key()).isEqualTo("CALORIES_PER_DAY");
        assertThat(facts.get(0).number()).isEqualTo(1);
    }

    @Test
    @DisplayName("no breakfast entry leaves the breakfast fact out, and lunch alone still gives the lunch and dinner fact")
    void missingMeals() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("160"), null, null, null,
                List.of(new NutritionInsight.MealProtein("LUNCH", "Lunch", new BigDecimal("48")),
                        new NutritionInsight.MealProtein("SNACK", "Snack", new BigDecimal("10"))));

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).extracting(ReadingFact::key).containsExactly("PROTEIN_PER_DAY", "PROTEIN_LUNCH_DINNER");
        assertThat(facts.get(1).value()).isEqualTo("48");
        assertThat(facts.get(1).note()).isEqualTo("30% of the day");
    }

    @Test
    @DisplayName("neither lunch nor dinner leaves that fact out, and breakfast alone gives the breakfast fact")
    void breakfastOnly() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("100"), null, null, null,
                List.of(new NutritionInsight.MealProtein("breakfast", "Breakfast", new BigDecimal("40"))));

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).extracting(ReadingFact::key).containsExactly("PROTEIN_PER_DAY", "PROTEIN_BREAKFAST");
        assertThat(facts.get(1).note()).isEqualTo("40% of the day");
    }

    @Test
    @DisplayName("without the day's protein a meal fact has no share to state")
    void noDayTotalMeansNoShare() {
        NutritionInsight insight = PageInsightFixtures.nutrition(null, null, null, null,
                List.of(new NutritionInsight.MealProtein("BREAKFAST", "Breakfast", new BigDecimal("40"))));

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).note()).isEmpty();
    }

    @Test
    @DisplayName("a period of NONE has no calories fact for the progress page to borrow")
    void noneHasNoCaloriesFact() {
        assertThat(NutritionFactsBuilder.caloriesFact(PageInsightFixtures.noNutrition())).isNull();
        assertThat(NutritionFactsBuilder.caloriesFact(null)).isNull();
    }
}
