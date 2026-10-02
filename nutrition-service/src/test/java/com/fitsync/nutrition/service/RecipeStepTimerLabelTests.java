package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitsync.nutrition.dto.RecipeStepTimerView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The short label of each timer, checked on step sentences copied from the V902 TheMealDB seed. */
class RecipeStepTimerLabelTests {

    private static final String KEFTA = "Heat a large skillet over medium-high heat. Brown patties in batches in the "
            + "hot skillet until crispy on both sides and no longer pink in the center, about 10 minutes.";

    private record Case(String text, String ingredient, String label) {
    }

    private static final List<Case> CASES = List.of(
            new Case(KEFTA, null, "Brown patties"),
            new Case("Simmer the sauce for 20 minutes.", null, "Simmer the sauce"),
            new Case("Boil for 5 minutes.", "Prawns", "Boil · Prawns"),
            new Case("Boil for 5 minutes.", null, "Boil"),
            new Case("Steam for 30-45 minutes over a medium heat.", "Pudding", "Steam · Pudding"),
            new Case("Chill for 20 minutes.", "Dough", "Chill · Dough"),
            new Case("Grill for about 15 mins, turning and brushing with more oil halfway, until browned and "
                    + "softened.", "Aubergine", "Grill · Aubergine"),
            new Case("Simmer for 5 minutes.", null, "Simmer"),
            new Case("Cook the arepas on a griddle or skillet over medium heat for 2–3 minutes on each side until "
                    + "golden.", "Arepas", "Cook the arepas"),
            new Case("Bake for 20 to 30 minutes uncovered, until bubbly and cheese has melted and started to brown "
                    + "on top.", "Pie", "Bake · Pie"),
            new Case("Simmer, stirring occasionally, for about 30 minutes, or until the sauce is thickened.", null,
                    "Simmer"),
            new Case("Cook for 1 hour and 15 minutes, or until the rice pudding thickens to desire consistency.",
                    "Rice", "Cook · Rice"),
            new Case("Cook: Cook the arepas on a griddle or skillet over medium heat for 2–3 minutes on each side "
                    + "until golden.", null, "Cook the arepas"),
            new Case("Cook gently stirring the whole time until the eggs are set, 2 to 5 minutes.", null,
                    "Cook gently stirring the whole"),
            new Case("Spread the mixture into the cake tin and bake for 50-55 mins or until firm in the centre.",
                    "Cake", "Bake · Cake"));

    @Test
    void realSentencesGetShortLabels() {
        assertTrue(CASES.size() >= 12);
        for (Case c : CASES) {
            List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(c.text(), 3, c.ingredient());
            assertEquals(1, timers.size(), c.text());
            assertEquals(c.label(), timers.get(0).label(), c.text());
        }
    }

    @Test
    void algerianKeftaStepGetsATenMinuteTimerThatIsNotEachSide() {
        List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(KEFTA, 3, null);
        assertEquals(1, timers.size());
        RecipeStepTimerView timer = timers.get(0);
        assertEquals(600, timer.seconds());
        assertEquals(600, timer.maxSeconds());
        assertEquals("10 minutes", timer.phrase());
        assertFalse(timer.eachSide(), "\"both sides\" here describes doneness, not a flip");
        assertEquals("Brown patties", timer.label());
    }

    @Test
    void bothSidesStillMeansEachSideWhenItIsNotAnUntilClause() {
        assertTrue(RecipeStepAnalyzer.timers("Fry for 3 minutes on both sides until golden.").get(0).eachSide());
        assertFalse(RecipeStepAnalyzer.timers("Fry the fish until golden on both sides, about 6 minutes.")
                .get(0).eachSide());
    }

    @Test
    void sameLabelInOneStepGetsANumberOnTheSecondTimer() {
        List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(
                "Boil for 5 minutes. Drain. Boil for 10 minutes.", 1, "Rice");
        assertEquals(List.of("Boil · Rice", "Boil · Rice · 2"),
                timers.stream().map(RecipeStepTimerView::label).toList());
    }

    @Test
    void anEachSideTimerIsOneTimerWithOneLabel() {
        List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers("Fry for 2 minutes each side.", 1, "Fish");
        assertEquals(1, timers.size());
        assertEquals("Fry · Fish", timers.get(0).label());
    }

    @Test
    void longLabelsAreCutOnAWordBoundary() {
        String label = RecipeStepAnalyzer.timers("Marinate chicken breasts thighs drumsticks for 30 minutes.")
                .get(0).label();
        assertEquals("Marinate chicken breasts thighs", label);
        assertTrue(label.length() <= 32);
        String longName = RecipeStepAnalyzer.timers("Bake for 25 minutes.", 1, "Chocolate Fudge Brownie Cheesecake")
                .get(0).label();
        assertEquals("Bake · Chocolate Fudge Brownie", longName);
    }

    @Test
    void everyV902TimerHasANonEmptyLabelOfAtMost32Characters() throws IOException {
        Path seed = Path.of("src/main/resources/db/seed/V902__themealdb_system_recipes.sql");
        Pattern row = Pattern.compile("^\\('[0-9a-f-]{36}','[0-9a-f-]{36}',(\\d+),'((?:[^']|'')*)',timestamp");
        int steps = 0;
        int timers = 0;
        List<String> bad = new ArrayList<>();
        for (String line : Files.readAllLines(seed, StandardCharsets.UTF_8)) {
            Matcher match = row.matcher(line);
            if (!match.find() || !line.contains("'themealdb-import'")) {
                continue;
            }
            steps++;
            String text = match.group(2).replace("''", "'");
            for (RecipeStepTimerView timer : RecipeStepAnalyzer.timers(text, Integer.parseInt(match.group(1)), null)) {
                timers++;
                String label = timer.label();
                if (label == null || label.isBlank() || label.length() > 32 || !label.equals(label.strip())) {
                    bad.add(text + " -> " + label);
                }
            }
        }
        assertTrue(steps > 1000, "steps found: " + steps);
        assertTrue(timers > 100, "timers found: " + timers);
        assertTrue(bad.isEmpty(), "bad labels: " + bad);
    }
}
