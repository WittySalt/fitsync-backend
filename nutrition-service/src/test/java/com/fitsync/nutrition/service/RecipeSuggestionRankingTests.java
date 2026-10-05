package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fitsync.nutrition.service.RecipeSuggestionRanking.Context;
import com.fitsync.nutrition.service.RecipeSuggestionRanking.Rank;
import com.fitsync.nutrition.service.RecipeSuggestionRanking.Serving;
import com.fitsync.nutrition.service.RecipeSuggestionRanking.Source;
import org.junit.jupiter.api.Test;

class RecipeSuggestionRankingTests {

    private static Serving serving(int kcal, String protein, String nutrient) {
        return new Serving(kcal, new BigDecimal(protein), nutrient == null ? null : new BigDecimal(nutrient));
    }

    private static Serving serving(int kcal, Source source, boolean recent) {
        return new Serving(kcal, BigDecimal.ZERO, null, false, source, recent, 0);
    }

    private static Context today(Integer kcalLeft, String proteinShort, String code, String reference) {
        return new Context(kcalLeft, proteinShort == null ? null : new BigDecimal(proteinShort), code,
                reference == null ? null : new BigDecimal(reference));
    }

    private static List<Integer> order(Context context, Serving... servings) {
        List<Serving> sorted = new ArrayList<>(List.of(servings));
        sorted.sort(Comparator.comparing(item -> RecipeSuggestionRanking.rank(context, item), Rank.BEST_FIRST));
        return sorted.stream().map(Serving::kcal).toList();
    }

    // Budget fit

    @Test
    void aServingWithinFifteenPercentOfOneMealScoresTheFullBudgetPoints() {
        // 600 left and no meal size, so one meal is 600 and the band is 510 to 690.
        Context context = today(600, null, null, null);
        assertEquals(100.0, RecipeSuggestionRanking.rank(context, serving(600, "0", null)).score(), 0.0001);
        assertEquals(100.0, RecipeSuggestionRanking.rank(context, serving(520, "0", null)).score(), 0.0001);
        assertEquals(0, RecipeSuggestionRanking.rank(context, serving(520, "0", null)).tier());
    }

    @Test
    void outsideTheBandTheScoreFallsLinearlyToZeroAtTwiceTheDistance() {
        // One meal is 400, the band is 60 wide, so 340 and 460 still score 100 and 280 and 520 score 0.
        Context context = new Context(1200, null, null, null, null, 400);
        assertEquals(100.0, RecipeSuggestionRanking.rank(context, serving(340, "0", null)).score(), 0.0001);
        assertEquals(50.0, RecipeSuggestionRanking.rank(context, serving(310, "0", null)).score(), 0.0001);
        assertEquals(50.0, RecipeSuggestionRanking.rank(context, serving(490, "0", null)).score(), 0.0001);
        assertEquals(0.0, RecipeSuggestionRanking.rank(context, serving(280, "0", null)).score(), 0.0001);
        assertEquals(0.0, RecipeSuggestionRanking.rank(context, serving(100, "0", null)).score(), 0.0001);
    }

    @Test
    void oneMealIsTheSmallerOfTheCaloriesLeftAndAThirdOfTheTarget() {
        Context plenty = new Context(1500, null, null, null, null, 700);
        assertEquals(List.of(680, 900, 300),
                order(plenty, serving(300, "0", null), serving(680, "0", null), serving(900, "0", null)));
        Context little = new Context(400, null, null, null, null, 700);
        assertEquals(List.of(380, 250, 500), order(little, serving(500, "0", null), serving(250, "0", null),
                serving(380, "0", null)));
    }

    @Test
    void aServingOverWhatIsLeftIsAlwaysBehindEveryServingUnderIt() {
        Context context = today(300, "40", null, null);
        // The first serving is far under and has no protein; the second is just over and has plenty.
        Rank under = RecipeSuggestionRanking.rank(context, serving(50, "0", null));
        Rank over = RecipeSuggestionRanking.rank(context, serving(310, "60", null));
        assertEquals(0, under.tier());
        assertEquals(2, over.tier());
        assertTrue(Rank.BEST_FIRST.compare(under, over) < 0);
    }

    @Test
    void whenNothingIsLeftTheLightestServingWins() {
        Context context = today(-50, null, null, null);
        assertEquals(List.of(150, 300, 600), order(context, serving(600, "0", null), serving(150, "0", null),
                serving(300, "0", null)));
    }

    @Test
    void withoutACalorieFigureOnlyTheBonusesCount() {
        Rank rank = RecipeSuggestionRanking.rank(today(null, "40", null, null), serving(900, "40", null));
        assertEquals(0, rank.tier());
        assertEquals(RecipeSuggestionRanking.MAX_BONUS, rank.score(), 0.0001);
    }

    // Bonuses and the penalty

    @Test
    void proteinTowardsTheShortfallRaisesTheScoreUpToTheBonusCap() {
        Context context = today(500, "40", null, null);
        double none = RecipeSuggestionRanking.rank(context, serving(500, "0", null)).score();
        double half = RecipeSuggestionRanking.rank(context, serving(500, "20", null)).score();
        double more = RecipeSuggestionRanking.rank(context, serving(500, "80", null)).score();
        assertEquals(RecipeSuggestionRanking.MAX_BONUS / 2, half - none, 0.0001);
        assertEquals(RecipeSuggestionRanking.MAX_BONUS, more - none, 0.0001);
    }

    @Test
    void noProteinBonusWhenNothingIsShort() {
        Context context = today(500, "0", null, null);
        assertEquals(RecipeSuggestionRanking.rank(context, serving(500, "0", null)).score(),
                RecipeSuggestionRanking.rank(context, serving(500, "50", null)).score(), 0.0001);
    }

    @Test
    void theLowestNutrientEarnsTheFullBonusAtHalfOfItsReference() {
        Context context = today(500, null, "POTASSIUM", "3400");
        double none = RecipeSuggestionRanking.rank(context, serving(500, "0", null)).score();
        double half = RecipeSuggestionRanking.rank(context, serving(500, "0", "1700")).score();
        double quarter = RecipeSuggestionRanking.rank(context, serving(500, "0", "850")).score();
        assertEquals(RecipeSuggestionRanking.MAX_BONUS, half - none, 0.0001);
        assertEquals(RecipeSuggestionRanking.MAX_BONUS / 2, quarter - none, 0.0001);
    }

    @Test
    void aServingHighInSugarLosesTheFullProteinBonusAmount() {
        Context context = new Context(1500, null, null, null, null, 700);
        double plain = RecipeSuggestionRanking.rank(context,
                new Serving(700, BigDecimal.ZERO, null, false, Source.LIBRARY, false, 0)).score();
        double sweet = RecipeSuggestionRanking.rank(context,
                new Serving(700, BigDecimal.ZERO, null, true, Source.LIBRARY, false, 0)).score();
        assertEquals(RecipeSuggestionRanking.MAX_BONUS, plain - sweet, 0.0001);
    }

    @Test
    void aSweetServingThatFitsDoesNotOutrankASaladThatFitsWithProtein() {
        Context context = today(500, "40", null, null);
        Rank dessert = RecipeSuggestionRanking.rank(context,
                new Serving(500, new BigDecimal("4"), null, true, Source.LIBRARY, false, 0));
        Rank salad = RecipeSuggestionRanking.rank(context, serving(500, "30", null));
        assertTrue(Rank.BEST_FIRST.compare(salad, dessert) < 0);
    }

    // Recent tier and source order

    @Test
    void aRecipeCookedInTheLastWeekMovesDownOneTierButStaysUnderTheOverBudgetOnes() {
        Context context = today(500, null, null, null);
        Rank fresh = RecipeSuggestionRanking.rank(context, serving(500, Source.LIBRARY, false));
        Rank recent = RecipeSuggestionRanking.rank(context, serving(500, Source.LIBRARY, true));
        Rank over = RecipeSuggestionRanking.rank(context, serving(520, Source.LIBRARY, false));
        assertEquals(0, fresh.tier());
        assertEquals(1, recent.tier());
        assertEquals(2, over.tier());
        assertTrue(Rank.BEST_FIRST.compare(fresh, recent) < 0);
        assertTrue(Rank.BEST_FIRST.compare(recent, over) < 0);
    }

    @Test
    void equalScoresFallBackToMineThenRecentThenLibrary() {
        Context context = today(500, null, null, null);
        List<Rank> ranks = new ArrayList<>(List.of(
                RecipeSuggestionRanking.rank(context, serving(500, Source.LIBRARY, false)),
                RecipeSuggestionRanking.rank(context, serving(500, Source.MINE, false)),
                RecipeSuggestionRanking.rank(context, serving(500, Source.RECENT, false))));
        ranks.sort(Rank.BEST_FIRST);
        assertEquals(List.of(Source.MINE.ordinal(), Source.RECENT.ordinal(), Source.LIBRARY.ordinal()),
                ranks.stream().map(Rank::sourceOrder).toList());
    }

    // Reasons

    @Test
    void aServingThatFitsAndClosesTheGapSaysBoth() {
        Context context = today(620, "41", null, null);
        assertEquals("Fits your 620 left and closes most of the protein gap",
                RecipeSuggestionRanking.reason(context, serving(620, "38", null)));
    }

    @Test
    void aServingThatOnlyFitsSaysItFits() {
        Context context = today(620, "41", null, null);
        assertEquals("Fits your 620 left", RecipeSuggestionRanking.reason(context, serving(620, "6", null)));
    }

    @Test
    void theProteinReasonCountsGramsUnlessMostOfTheGapIsClosed() {
        // 300 kcal against a meal of 620 earns no budget points, so protein gives the most.
        Context context = today(620, "41", null, null);
        assertEquals("Closes most of the protein gap", RecipeSuggestionRanking.reason(context, serving(200, "30", null)));
        assertEquals("Adds 10 g of protein", RecipeSuggestionRanking.reason(context, serving(200, "10", null)));
    }

    @Test
    void theNutrientReasonNamesTheLowestNutrient() {
        Context context = new Context(620, null, "IRON", "Iron", new BigDecimal("8"), null);
        assertEquals("Brings Iron, your lowest this week",
                RecipeSuggestionRanking.reason(context, serving(200, "0", "4")));
    }

    @Test
    void aServingOverWhatIsLeftNeverSaysItFits() {
        Context context = today(300, null, null, null);
        assertEquals("A balanced option for tonight", RecipeSuggestionRanking.reason(context, serving(400, "0", null)));
    }

    @Test
    void aRecipeWithNothingToSayFallsBackToHowOftenItWasCooked() {
        Context context = today(300, null, null, null);
        assertEquals("You cooked it 3 times this month", RecipeSuggestionRanking.reason(context,
                new Serving(400, BigDecimal.ZERO, null, false, Source.MINE, false, 3)));
        assertEquals("You cooked it once this month", RecipeSuggestionRanking.reason(context,
                new Serving(400, BigDecimal.ZERO, null, false, Source.MINE, false, 1)));
    }
}
