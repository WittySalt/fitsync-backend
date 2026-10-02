package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fitsync.nutrition.service.RecipeFitScorer.Context;
import com.fitsync.nutrition.service.RecipeFitScorer.Rank;
import com.fitsync.nutrition.service.RecipeFitScorer.Serving;
import org.junit.jupiter.api.Test;

class RecipeFitScorerTests {

    private static Serving serving(int kcal, String protein, String nutrient) {
        return new Serving(kcal, new BigDecimal(protein), nutrient == null ? null : new BigDecimal(nutrient));
    }

    private static Context today(Integer kcalLeft, String proteinShort, String code, String reference) {
        return new Context(kcalLeft, proteinShort == null ? null : new BigDecimal(proteinShort), code,
                reference == null ? null : new BigDecimal(reference));
    }

    private static List<Integer> order(Context context, Serving... servings) {
        List<Serving> sorted = new ArrayList<>(List.of(servings));
        sorted.sort(Comparator.comparing(item -> RecipeFitScorer.rank(context, item), Rank.BEST_FIRST));
        return sorted.stream().map(Serving::kcal).toList();
    }

    @Test
    void servingsUnderWhatIsLeftComeFirstAndTheClosestLeadsThem() {
        Context context = today(500, null, null, null);
        assertEquals(List.of(480, 300, 100, 520, 700),
                order(context, serving(100, "0", null), serving(700, "0", null), serving(480, "0", null),
                        serving(300, "0", null), serving(520, "0", null)));
    }

    @Test
    void aServingExactlyOnWhatIsLeftIsUnderAndScoresTheMostForCloseness() {
        Rank exact = RecipeFitScorer.rank(today(400, null, null, null), serving(400, "0", null));
        assertEquals(0, exact.tier());
        assertEquals(100.0, exact.score(), 0.0001);
    }

    @Test
    void whenNothingIsLeftTheLightestServingWins() {
        Context context = today(-50, null, null, null);
        assertEquals(List.of(150, 300, 600), order(context, serving(600, "0", null), serving(150, "0", null),
                serving(300, "0", null)));
    }

    @Test
    void aProteinBonusCannotLiftAServingOverTheLimitAboveOneUnderIt() {
        Context context = today(300, "40", null, null);
        // The first serving is far under and has no protein; the second is just over and has plenty.
        Rank under = RecipeFitScorer.rank(context, serving(50, "0", null));
        Rank over = RecipeFitScorer.rank(context, serving(310, "60", null));
        assertTrue(Rank.BEST_FIRST.compare(under, over) < 0);
    }

    @Test
    void proteinTowardsTheShortfallRaisesTheScoreUpToTheBonusCap() {
        Context context = today(500, "40", null, null);
        double none = RecipeFitScorer.rank(context, serving(400, "0", null)).score();
        double half = RecipeFitScorer.rank(context, serving(400, "20", null)).score();
        double more = RecipeFitScorer.rank(context, serving(400, "80", null)).score();
        assertEquals(RecipeFitScorer.MAX_BONUS / 2, half - none, 0.0001);
        assertEquals(RecipeFitScorer.MAX_BONUS, more - none, 0.0001);
    }

    @Test
    void noProteinBonusWhenNothingIsShort() {
        Context context = today(500, "0", null, null);
        assertEquals(RecipeFitScorer.rank(context, serving(400, "0", null)).score(),
                RecipeFitScorer.rank(context, serving(400, "50", null)).score(), 0.0001);
    }

    @Test
    void theLowestNutrientEarnsTheFullBonusAtHalfOfItsReference() {
        Context context = today(500, null, "POTASSIUM", "3400");
        double none = RecipeFitScorer.rank(context, serving(400, "0", null)).score();
        double half = RecipeFitScorer.rank(context, serving(400, "0", "1700")).score();
        double quarter = RecipeFitScorer.rank(context, serving(400, "0", "850")).score();
        assertEquals(RecipeFitScorer.MAX_BONUS, half - none, 0.0001);
        assertEquals(RecipeFitScorer.MAX_BONUS / 2, quarter - none, 0.0001);
    }

    @Test
    void withoutACalorieTargetOnlyTheBonusesCount() {
        Rank rank = RecipeFitScorer.rank(today(null, "40", null, null), serving(900, "40", null));
        assertEquals(0, rank.tier());
        assertEquals(RecipeFitScorer.MAX_BONUS, rank.score(), 0.0001);
    }
    @Test
    void withMuchOfTheDayLeftTheServingNearestOneMealLeadsNotTheOneThatUsesItAllUp() {
        Context context = new Context(1500, null, null, null, 700);
        assertEquals(List.of(680, 900, 300, 1450),
                order(context, serving(1450, "0", null), serving(680, "0", null), serving(300, "0", null),
                        serving(900, "0", null)));
    }

    @Test
    void whenLessThanOneMealIsLeftWhatIsLeftIsTheMeal() {
        Context context = new Context(400, null, null, null, 700);
        assertEquals(List.of(380, 250, 500), order(context, serving(500, "0", null), serving(250, "0", null),
                serving(380, "0", null)));
    }

    @Test
    void aServingHighInSugarLosesTheBonusAmount() {
        Context context = new Context(1500, null, null, null, 700);
        double plain = RecipeFitScorer.rank(context, new Serving(700, BigDecimal.ZERO, null, false)).score();
        double sweet = RecipeFitScorer.rank(context, new Serving(700, BigDecimal.ZERO, null, true)).score();
        assertEquals(RecipeFitScorer.MAX_BONUS, plain - sweet, 0.0001);
    }
}
