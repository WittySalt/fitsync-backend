package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import com.fitsync.nutrition.service.RecipeInsightService.Totals;
import org.junit.jupiter.api.Test;

/** The tag thresholds are stated per 100 g of the recipe, so these checks sit exactly on and next to each limit. */
class RecipeInsightServiceTests {

    private static final BigDecimal HUNDRED_GRAMS = new BigDecimal("100");

    @Test
    void proteinNeedsThreeGramsAndAFifthOfTheEnergy() {
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_PROTEIN),
                RecipeInsightService.tagsFor(totals("60", "3", "0", "0", "0"), HUNDRED_GRAMS));
        // 61 kcal makes 12 kcal of protein just under a fifth of the energy.
        assertTrue(RecipeInsightService.tagsFor(totals("61", "3", "0", "0", "0"), HUNDRED_GRAMS).isEmpty());
        // Plenty of the energy from protein, but under 3 g per 100 g.
        assertTrue(RecipeInsightService.tagsFor(totals("10", "2.9", "0", "0", "0"), HUNDRED_GRAMS).isEmpty());
    }

    @Test
    void fibreStartsAtSixGramsPer100Grams() {
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_FIBRE),
                RecipeInsightService.tagsFor(totals("400", "0", "6", "0", "0"), HUNDRED_GRAMS));
        assertTrue(RecipeInsightService.tagsFor(totals("400", "0", "5.99", "0", "0"), HUNDRED_GRAMS).isEmpty());
    }

    @Test
    void sugarAndSaltNeedMoreThanTheirLimit() {
        assertTrue(RecipeInsightService.tagsFor(totals("400", "0", "0", "22.5", "600"), HUNDRED_GRAMS).isEmpty());
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_SUGAR),
                RecipeInsightService.tagsFor(totals("400", "0", "0", "22.6", "600"), HUNDRED_GRAMS));
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_SALT),
                RecipeInsightService.tagsFor(totals("400", "0", "0", "0", "601"), HUNDRED_GRAMS));
    }

    @Test
    void theAmountsAreScaledToTheWeightOfTheWholeRecipe() {
        // 12 g of fibre in 200 g is 6 g per 100 g.
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_FIBRE),
                RecipeInsightService.tagsFor(totals("400", "0", "12", "0", "0"), new BigDecimal("200")));
        // 5 g of fibre in 100 g of recipe is below the limit.
        assertTrue(RecipeInsightService.tagsFor(totals("400", "0", "12", "0", "0"), new BigDecimal("250")).isEmpty());
    }

    @Test
    void aRecipeWithoutAKnownWeightEarnsNoTags() {
        assertTrue(RecipeInsightService.tagsFor(totals("400", "50", "50", "50", "5000"), BigDecimal.ZERO).isEmpty());
    }

    @Test
    void severalTagsComeInAFixedOrder() {
        assertEquals(List.of(RecipeInsightService.TAG_HIGH_PROTEIN, RecipeInsightService.TAG_HIGH_FIBRE,
                        RecipeInsightService.TAG_HIGH_SUGAR, RecipeInsightService.TAG_HIGH_SALT),
                RecipeInsightService.tagsFor(totals("100", "20", "10", "30", "900"), HUNDRED_GRAMS));
    }

    private static Totals totals(String kcal, String protein, String fibre, String sugar, String sodium) {
        Totals totals = new Totals();
        totals.kcal = new BigDecimal(kcal);
        totals.protein = new BigDecimal(protein);
        totals.fibre = new BigDecimal(fibre);
        totals.sugar = new BigDecimal(sugar);
        totals.sodium = new BigDecimal(sodium);
        return totals;
    }
}
