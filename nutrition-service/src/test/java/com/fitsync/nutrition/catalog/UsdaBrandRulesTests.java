package com.fitsync.nutrition.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsdaBrandRulesTests {

    private static String[] rewrite(String name) {
        UsdaBrandRules.Rewrite r = UsdaBrandRules.rewrite(name).orElseThrow();
        return new String[] {r.brandName(), r.displayName()};
    }

    @Test
    void aBrandBeforeTheFirstCommaIsMovedOutAndTheRestIsTitleCased() {
        assertThat(rewrite("McDONALD'S, BIG MAC")).containsExactly("McDONALD'S", "Big Mac");
        assertThat(rewrite("McDONALD'S, Chicken McNUGGETS")).containsExactly("McDONALD'S", "Chicken McNuggets");
        assertThat(rewrite("WENDY'S, Chicken Nuggets")).containsExactly("WENDY'S", "Chicken Nuggets");
        assertThat(rewrite("BURGER KING, Whopper, no cheese")).containsExactly("BURGER KING", "Whopper, no cheese");
        assertThat(rewrite("KFC, Crispy Chicken Strips")).containsExactly("KFC", "Crispy Chicken Strips");
        assertThat(rewrite("SCHIFF,TIGER'S MILK BAR")).containsExactly("SCHIFF", "Tiger's Milk Bar");
    }

    @Test
    void acronymsStayAndSmallWordsAndWithAreLowerCased() {
        assertThat(rewrite("TACO BELL, BBQ CHICKEN W/ CHEESE AND SAUCE"))
                .containsExactly("TACO BELL", "BBQ Chicken with Cheese and Sauce");
        assertThat(rewrite("SUBWAY, sandwich w/ turkey")).containsExactly("SUBWAY", "Sandwich with turkey");
        assertThat(rewrite("KFC, biscuit")).containsExactly("KFC", "Biscuit");
        assertThat(UsdaBrandRules.titleCase("A TASTE OF HOME")).isEqualTo("A Taste of Home");
        assertThat(UsdaBrandRules.titleCase("FILET-O-FISH")).isEqualTo("Filet-O-Fish");
        assertThat(UsdaBrandRules.titleCase("BREAKSTONE'S Sour Cream")).isEqualTo("Breakstone's Sour Cream");
        assertThat(UsdaBrandRules.titleCase("THIN 'N CRISPY Crust")).isEqualTo("Thin 'N Crispy Crust");
        assertThat(UsdaBrandRules.titleCase("BIG MAC (without Big Mac Sauce)"))
                .isEqualTo("Big Mac (without Big Mac Sauce)");
    }

    @Test
    void aBrandFollowedByASpaceIsRecognisedForTheMakersThatWriteItThatWay() {
        assertThat(rewrite("PIZZA HUT 14\" Pepperoni Pizza, THIN 'N CRISPY Crust"))
                .containsExactly("PIZZA HUT", "14\" Pepperoni Pizza, Thin 'N Crispy Crust");
        assertThat(rewrite("HORMEL ALWAYS TENDER, Pork Tenderloin, Teriyaki-Flavored"))
                .containsExactly("HORMEL", "Always Tender, Pork Tenderloin, Teriyaki-Flavored");
        assertThat(rewrite("KRAFT VELVEETA Pasteurized Process Cheese Spread"))
                .containsExactly("KRAFT", "Velveeta Pasteurized Process Cheese Spread");
        assertThat(rewrite("McDONALD'S Bacon Ranch Salad with Crispy Chicken"))
                .containsExactly("McDONALD'S", "Bacon Ranch Salad with Crispy Chicken");
    }

    @Test
    void aliasesAndCaseMapToTheListedBrand() {
        assertThat(rewrite("WEND'YS, Chicken Nuggets")[0]).isEqualTo("WENDY'S");
        assertThat(rewrite("Kraft Foods, Shake N Bake Original Recipe")[0]).isEqualTo("KRAFT");
        assertThat(rewrite("Nabisco, Nabisco Ritz Crackers")).containsExactly("NABISCO", "Nabisco Ritz Crackers");
        assertThat(rewrite("CAMPBELL'S, Cream of Mushroom Soup, condensed")[0]).isEqualTo("CAMPBELL'S");
    }

    @Test
    void namesWithoutAListedBrandAreLeftAlone() {
        assertThat(UsdaBrandRules.rewrite("Chicken, nuggets, white meat, precooked")).isEmpty();
        assertThat(UsdaBrandRules.rewrite("Ice cream bar, stick or nugget, with crunch coating")).isEmpty();
        // The brand is not at the start, and a brand with nothing after it has nothing to rename.
        assertThat(UsdaBrandRules.rewrite("Cereals ready-to-eat, QUAKER, CAP'N CRUNCH")).isEmpty();
        assertThat(UsdaBrandRules.rewrite("Gravy, CAMPBELL'S, chicken")).isEmpty();
        assertThat(UsdaBrandRules.rewrite("KFC,")).isEmpty();
        assertThat(UsdaBrandRules.rewrite("Post office pudding")).isEmpty();
        assertThat(UsdaBrandRules.rewrite("Oats (Includes foods for USDA's Food Distribution Program)")).isEmpty();
        assertThat(UsdaBrandRules.rewrite(null)).isEmpty();
    }

    @Test
    void theBrandListHoldsTheOwnersNames() {
        assertThat(UsdaBrandRules.brandNames()).contains("McDONALD'S", "WENDY'S", "T.G.I. FRIDAY'S", "CARL'S JR.",
                "COCA-COLA", "KELLOGG'S", "GENERAL MILLS", "POST").doesNotHaveDuplicates();
    }
}
