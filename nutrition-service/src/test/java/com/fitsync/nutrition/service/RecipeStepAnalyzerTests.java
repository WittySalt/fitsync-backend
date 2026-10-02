package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitsync.nutrition.dto.RecipeStepTimerView;
import com.fitsync.nutrition.service.RecipeStepAnalyzer.IngredientRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeStepAnalyzerTests {

    private static IngredientRef ingredient(String foodName, String displayText) {
        return new IngredientRef(UUID.randomUUID(), foodName, displayText);
    }

    private static List<List<UUID>> match(List<String> steps, IngredientRef... refs) {
        return RecipeStepAnalyzer.ingredientIdsByStep(steps, List.of(refs));
    }

    private static RecipeStepTimerView only(String text) {
        List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(text);
        assertEquals(1, timers.size(), text);
        return timers.get(0);
    }

    // ingredient names

    @Test
    void ingredientNameIsTheWordAfterTheMeasure() {
        assertEquals("Salmon", RecipeStepAnalyzer.ingredientName("400g Salmon"));
        assertEquals("Olive Oil", RecipeStepAnalyzer.ingredientName("3 tbs Olive Oil"));
        assertEquals("Lime", RecipeStepAnalyzer.ingredientName("zest and juice of 1 Lime"));
        assertEquals("Salt", RecipeStepAnalyzer.ingredientName("Pinch of Salt"));
        assertEquals("Cream of tartar", RecipeStepAnalyzer.ingredientName("Cream of tartar"));
        assertEquals("Avocado", RecipeStepAnalyzer.ingredientName("2 large Avocado"));
        assertEquals("red snapper", RecipeStepAnalyzer.ingredientName("2 pounds red snapper"));
        assertEquals("Cheddar", RecipeStepAnalyzer.ingredientName("8 ounces Cheddar"));
        assertEquals("", RecipeStepAnalyzer.ingredientName(null));
    }

    @Test
    void ingredientNameDropsPreparationAndUseWords() {
        assertEquals("Onion", RecipeStepAnalyzer.ingredientName("1 Chopped Onion"));
        assertEquals("Garlic", RecipeStepAnalyzer.ingredientName("2 cloves peeled crushed Garlic"));
        assertEquals("Basil Leaves", RecipeStepAnalyzer.ingredientName("To serve Basil Leaves"));
        assertEquals("Red Onions", RecipeStepAnalyzer.ingredientName("2 finely sliced Red Onions"));
        assertEquals("Parsley", RecipeStepAnalyzer.ingredientName("Large handful Parsley"));
        assertEquals("Oil", RecipeStepAnalyzer.ingredientName("For frying Oil"));
        assertEquals("Onion", RecipeStepAnalyzer.ingredientName("1 Onion, finely chopped"));
        assertEquals("Cinnamon", RecipeStepAnalyzer.ingredientName("Pinch Ground Cinnamon"));
        assertEquals("Salt", RecipeStepAnalyzer.ingredientName("Salt to taste"));
    }

    @Test
    void ingredientNameKeepsTheLineWhenOnlyNoiseWordsAreWritten() {
        assertEquals("Garnish", RecipeStepAnalyzer.ingredientName("Garnish"));
    }

    @Test
    void stepMatchingStillFindsIngredientsWrittenWithPreparationWords() {
        IngredientRef onion = ingredient("Onions, raw", "1 finely sliced Red Onions");
        assertEquals(List.of(List.of(onion.id())), match(List.of("Fry the red onions until soft."), onion));
    }

    // titles

    @Test
    void titleIsTheFirstSentenceWithoutItsEndMark() {
        assertEquals("Heat the oil", RecipeStepAnalyzer.title("Heat the oil. Add the onion and stir.", 1));
        assertEquals("Is it done", RecipeStepAnalyzer.title("Is it done? Serve.", 1));
        assertEquals("Serve at once", RecipeStepAnalyzer.title("Serve at once! Enjoy.", 1));
    }

    @Test
    void titleStopsAtALineEndAndKeepsDecimalNumbersTogether() {
        assertEquals("Mix the flour", RecipeStepAnalyzer.title("Mix the flour\nthen add the milk", 1));
        assertEquals("Add 2.5 kg of flour", RecipeStepAnalyzer.title("Add 2.5 kg of flour. Mix.", 1));
    }

    @Test
    void longTitleIsCutAtTheLastCommaBeforeSixtyCharacters() {
        String text = "Heat the oil in a large heavy pan over medium heat, then add the chopped onion and cook slowly.";
        assertEquals("Heat the oil in a large heavy pan over medium heat", RecipeStepAnalyzer.title(text, 2));
    }

    @Test
    void longTitleIsCutAtAndOrThenWhicheverIsLastBeforeSixty() {
        assertEquals("Cut the potatoes into even pieces",
                RecipeStepAnalyzer.title("Cut the potatoes into even pieces and boil them until they are tender", 1));
        assertEquals("Whisk the eggs with the sugar until pale",
                RecipeStepAnalyzer.title("Whisk the eggs with the sugar until pale then fold in the flour slowly", 1));
    }

    @Test
    void longTitleWithoutABoundaryIsCutAtTheLastSpaceAndEndsWithAnEllipsis() {
        String text = "Preheat the oven to one hundred and eighty degrees celsius before you begin anything else";
        // "and" at index 42 is a boundary, so use text without one
        String plain = "Preheat the oven to one hundred eighty degrees celsius before beginning anything else today";
        String title = RecipeStepAnalyzer.title(plain, 3);
        assertTrue(title.endsWith("…"));
        assertTrue(title.length() <= 58, title);
        assertEquals("Preheat the oven to one hundred eighty degrees celsius…", title);
        assertEquals("Preheat the oven to one hundred", RecipeStepAnalyzer.title(text, 3));
    }

    @Test
    void titleNeverComesBackEmpty() {
        assertEquals("Step 4", RecipeStepAnalyzer.title("", 4));
        assertEquals("Step 5", RecipeStepAnalyzer.title("   ", 5));
        assertEquals("Step 6", RecipeStepAnalyzer.title(null, 6));
        assertEquals("Step 7", RecipeStepAnalyzer.title(". ", 7));
    }

    // ingredients

    @Test
    void ingredientIsFoundByTheWordAfterTheMeasure() {
        IngredientRef salmon = ingredient("Fish, salmon, Atlantic, farmed, raw", "400g Salmon");
        List<List<UUID>> used = match(List.of("Grill the SALMON for a while."), salmon);
        assertEquals(List.of(salmon.id()), used.get(0));
    }

    @Test
    void ingredientMatchesSimplePlurals() {
        IngredientRef tomato = ingredient("Tomatoes", "2 Tomato");
        IngredientRef berry = ingredient("Strawberry", "1 cup Strawberry");
        IngredientRef onions = ingredient("Onion", "3 Onions");
        List<List<UUID>> used = match(List.of("Chop the tomatoes, strawberries and the onion."), tomato, berry, onions);
        assertEquals(List.of(tomato.id(), berry.id(), onions.id()), used.get(0));
    }

    @Test
    void twoWordNameMatchesItsLastWord() {
        IngredientRef oil = ingredient("Olive oil", "2 tablespoons Olive oil");
        IngredientRef onion = ingredient("Onion", "2 Red onions");
        List<List<UUID>> used = match(List.of("Heat the oil.", "Fry the onion."), oil, onion);
        assertEquals(List.of(oil.id()), used.get(0));
        assertEquals(List.of(onion.id()), used.get(1));
    }

    @Test
    void ingredientMatchesOnlyWholeWords() {
        IngredientRef pea = ingredient("Peas", "100g Peas");
        List<List<UUID>> used = match(List.of("Peaches and a peacock."), pea);
        assertTrue(used.get(0).isEmpty());
    }

    @Test
    void ingredientBelongsToTheFirstStepThatMentionsIt() {
        IngredientRef garlic = ingredient("Garlic", "3 cloves Garlic");
        List<List<UUID>> used = match(List.of("Peel the garlic.", "Fry the garlic again.", "Serve."), garlic);
        assertEquals(List.of(garlic.id()), used.get(0));
        assertTrue(used.get(1).isEmpty());
        assertTrue(used.get(2).isEmpty());
    }

    @Test
    void ingredientNeverMentionedIsNotAssigned() {
        IngredientRef saffron = ingredient("Saffron", "1 pinch Saffron");
        assertTrue(match(List.of("Boil the rice.", "Serve."), saffron).stream().allMatch(List::isEmpty));
    }

    @Test
    void foodNameIsUsedWhenTheDisplayTextIsMissing() {
        IngredientRef rice = ingredient("Rice", null);
        IngredientRef butter = ingredient("Butter", "  ");
        List<List<UUID>> used = match(List.of("Boil the rice, then stir in butter."), rice, butter);
        assertEquals(List.of(rice.id(), butter.id()), used.get(0));
    }

    @Test
    void measureWordsAndFractionsAreSkippedInTheDisplayText() {
        IngredientRef flour = ingredient("Flour", "1/2 cup of Plain flour");
        IngredientRef milk = ingredient("Milk", "1½ tsp Milk");
        List<List<UUID>> used = match(List.of("Sift the flour.", "Warm the milk."), flour, milk);
        assertEquals(List.of(flour.id()), used.get(0));
        assertEquals(List.of(milk.id()), used.get(1));
    }

    // timers

    @Test
    void minutesAreRecognised() {
        RecipeStepTimerView timer = only("Simmer for 20 minutes.");
        assertEquals(1200, timer.seconds());
        assertEquals(1200, timer.maxSeconds());
        assertFalse(timer.eachSide());
        assertEquals("20 minutes", timer.phrase());
        assertEquals(60, only("Boil for 1 min.").seconds());
        assertEquals(300, only("Boil for 5 mins.").seconds());
    }

    @Test
    void rangesGiveALowerAndAnUpperBound() {
        RecipeStepTimerView dash = only("Bake for 50-55 mins until firm.");
        assertEquals(3000, dash.seconds());
        assertEquals(3300, dash.maxSeconds());
        assertEquals("50-55 mins", dash.phrase());
        RecipeStepTimerView words = only("Cook for 20 to 30 minutes uncovered.");
        assertEquals(1200, words.seconds());
        assertEquals(1800, words.maxSeconds());
        RecipeStepTimerView enDash = only("Cook for 2–3 minutes.");
        assertEquals(120, enDash.seconds());
        assertEquals(180, enDash.maxSeconds());
    }

    @Test
    void secondsAreRecognisedFromThirtyUp() {
        RecipeStepTimerView timer = only("Stir until it thickens, about 30 seconds.");
        assertEquals(30, timer.seconds());
        assertEquals("30 seconds", timer.phrase());
        RecipeStepTimerView range = only("Cover and cook for 30-60 secs.");
        assertEquals(30, range.seconds());
        assertEquals(60, range.maxSeconds());
        assertTrue(RecipeStepAnalyzer.timers("Stir for 20 seconds.").isEmpty());
    }

    @Test
    void hoursAndCompoundDurationsAreAdded() {
        assertEquals(7200, only("Roast for 2 hours.").seconds());
        assertEquals(3600, only("Simmer for 1 hr.").seconds());
        RecipeStepTimerView compound = only("Bake for 1 hour 15 mins until golden.");
        assertEquals(4500, compound.seconds());
        assertEquals(4500, compound.maxSeconds());
        assertEquals("1 hour 15 mins", compound.phrase());
        assertEquals(4500, only("Cook for 1 hour and 15 minutes.").seconds());
    }

    @Test
    void halvesAndHalfAnHourAreRecognised() {
        assertEquals(5400, only("Simmer for 1½ hours.").seconds());
        RecipeStepTimerView half = only("Chill for half an hour.");
        assertEquals(1800, half.seconds());
        assertEquals("half an hour", half.phrase());
        RecipeStepTimerView mixed = only("Cook for 1½-2 minutes each side.");
        assertEquals(90, mixed.seconds());
        assertEquals(120, mixed.maxSeconds());
    }

    @Test
    void eachSideIsReadFromTheSameSentence() {
        assertTrue(only("Fry for 3 minutes each side.").eachSide());
        assertTrue(only("Grill for 2-3 mins per side.").eachSide());
        assertTrue(only("Fry for 4 minutes on both sides.").eachSide());
        assertTrue(only("Cook for 5 minutes on each side until golden.").eachSide());
        assertFalse(RecipeStepAnalyzer.timers("Fry the fish each side. Cook it for 4 minutes.").get(0).eachSide());
    }

    @Test
    void durationsWithoutACookingWordInTheSameSentenceAreIgnored() {
        assertTrue(RecipeStepAnalyzer.timers("The sauce keeps for 3 days in the fridge.").isEmpty());
        assertTrue(RecipeStepAnalyzer.timers("Wait 10 minutes. Then cook the rice.").isEmpty());
        assertEquals(1, RecipeStepAnalyzer.timers("Wait 10 minutes. Then cook for 5 minutes.").size());
    }

    @Test
    void restAndChillCountAsCookingWords() {
        assertEquals(600, only("Rest for 10 minutes.").seconds());
        assertEquals(1800, only("Chill for 30 minutes before slicing.").seconds());
    }

    @Test
    void overnightDaysAndMoreThanThreeHoursAreIgnored() {
        assertTrue(RecipeStepAnalyzer.timers("Chill overnight.").isEmpty());
        assertTrue(RecipeStepAnalyzer.timers("Marinate for 8 hours or overnight.").isEmpty());
        assertTrue(RecipeStepAnalyzer.timers("Marinate for 2 days.").isEmpty());
        assertTrue(RecipeStepAnalyzer.timers("Roast for 3 hours 30 mins.").isEmpty());
        assertEquals(10800, only("Roast for 3 hours.").seconds());
    }

    @Test
    void severalDurationsInOneStepAreAllReturnedInOrder() {
        List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(
                "Bake for 50-55 mins. Cool for 10 minutes, then chill for 1 hour.");
        assertEquals(List.of("50-55 mins", "10 minutes", "1 hour"),
                timers.stream().map(RecipeStepTimerView::phrase).toList());
    }

    @Test
    void nullAndEmptyTextHaveNoTimers() {
        assertTrue(RecipeStepAnalyzer.timers(null).isEmpty());
        assertTrue(RecipeStepAnalyzer.timers("").isEmpty());
    }

    @Test
    void phraseIsTheExactTextOfTheStep() {
        String text = "Bake for 1 HOUR 15 Mins until golden.";
        RecipeStepTimerView timer = only(text);
        assertTrue(text.contains(timer.phrase()));
        assertEquals("1 HOUR 15 Mins", timer.phrase());
    }
}
