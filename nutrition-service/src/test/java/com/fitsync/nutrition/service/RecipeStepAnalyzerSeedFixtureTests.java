package com.fitsync.nutrition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitsync.nutrition.dto.RecipeStepTimerView;
import com.fitsync.nutrition.service.RecipeStepAnalyzer.IngredientRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Step texts copied from the V902 TheMealDB seed, checked for sensible output. */
class RecipeStepAnalyzerSeedFixtureTests {

    private record Fixture(String text, String title, List<String> phrases, List<Integer> seconds,
                           List<Integer> maxSeconds, boolean eachSide) {
    }

    private static Fixture fixture(String text, String title, List<String> phrases, List<Integer> seconds,
                                   List<Integer> maxSeconds, boolean eachSide) {
        return new Fixture(text, title, phrases, seconds, maxSeconds, eachSide);
    }

    private static final List<Fixture> FIXTURES = List.of(
            fixture("Place the prawns in a pot and add enough water to cover. Boil for 5 minutes. Drain, reserving "
                    + "the liquid, and set aside.",
                    "Place the prawns in a pot and add enough water to cover", List.of("5 minutes"), List.of(300),
                    List.of(300), false),
            fixture("Heat 2 tablespoons of oil in a saucepan. Add the onion; cook and stir until soft. Mix in the "
                    + "parsley, wine, tomatoes, garlic and remaining olive oil. Simmer, stirring occasionally, for "
                    + "about 30 minutes, or until the sauce is thickened.",
                    "Heat 2 tablespoons of oil in a saucepan", List.of("30 minutes"), List.of(1800), List.of(1800),
                    false),
            fixture("While the sauce is simmering, the prawns should become cool enough to handle.",
                    "While the sauce is simmering", List.of(), List.of(), List.of(), false),
            fixture("Preheat oven to 375 F degrees.",
                    "Preheat oven to 375 F degrees", List.of(), List.of(), List.of(), false),
            fixture("Bake for 20 to 30 minutes uncovered, until bubbly and cheese has melted and started to brown "
                    + "on top.",
                    "Bake for 20 to 30 minutes uncovered, until bubbly", List.of("20 to 30 minutes"), List.of(1200),
                    List.of(1800), false),
            fixture("Serve warm.", "Serve warm", List.of(), List.of(), List.of(), false),
            fixture("Break the chocolate into a heatproof bowl and place over a saucepan of gently simmering water "
                    + "and stir until it melts. (or melt in the microwave for 2-3 mins stirring occasionally)",
                    "Break the chocolate into a heatproof bowl", List.of("2-3 mins"), List.of(120), List.of(180),
                    false),
            fixture("Spread the mixture into the cake tin and bake for 50-55 mins or until firm in the centre and a "
                    + "skewer comes out cleanly. Cool for 10 minutes, then turn out and cool completely.",
                    "Spread the mixture into the cake tin", List.of("50-55 mins"),
                    List.of(3000), List.of(3300), false),
            fixture("Cook: Cook the arepas on a griddle or skillet over medium heat for 2–3 minutes on each side "
                    + "until golden. You can then bake them for a few minutes if you prefer them crispier.",
                    "Cook: Cook the arepas on a griddle or skillet over medium…", List.of("2–3 minutes"),
                    List.of(120), List.of(180), true),
            fixture("For the black pudding, cut the black pudding into 3-4 slices and remove the skin. Place on the "
                    + "grill plate and cook for 1½-2 minutes each side until slightly crispy.",
                    "For the black pudding", List.of("1½-2 minutes"), List.of(90), List.of(120), true),
            fixture("Reduce heat to medium low. Add the remaining milk and condensed milk. Stir with a wooden "
                    + "spoon. Cook for 1 hour and 15 minutes, or until the rice pudding thickens to desire "
                    + "consistency.",
                    "Reduce heat to medium low", List.of("1 hour and 15 minutes"), List.of(4500), List.of(4500),
                    false),
            fixture("For the green beans, bring a saucepan of salted water to the boil, add the beans and cook for "
                    + "4-5 minutes, or until just tender.",
                    "For the green beans", List.of("4-5 minutes"), List.of(240), List.of(300), false),
            fixture("Cook, gently stirring the whole time, until the scramble eggs are mostly set but still slightly "
                    + "wet and shiny, 2 to 5 minutes. Remove the eggs to a plate and, if needed, wipe out the pan.",
                    "Cook, gently stirring the whole time", List.of("2 to 5 minutes"), List.of(120), List.of(300), false),
            fixture("Turn out onto a piece of plastic wrap. Make a round ball of it by using your hands and roll it "
                    + "into the plastic. Place into the fridge for half an hour to cool.",
                    "Turn out onto a piece of plastic wrap", List.of(), List.of(), List.of(), false),
            fixture("Add the sliced olives, drained mozzarella balls, and chunks of tuna. Mix well and let the "
                    + "salad rest for at least half an hour to allow the flavours to mingle.",
                    "Add the sliced olives, drained mozzarella balls", List.of("half an hour"), List.of(1800), List.of(1800), false),
            fixture("Pour in the sauce and the cornstarch slurry (1 teaspoon cornstarch dissolved in 1 tablespoon of "
                    + "water). Stir until the sauce boils and thickens, about 30 seconds. Stir in the broccoli.",
                    "Pour in the sauce",
                    List.of("30 seconds"), List.of(30), List.of(30), false),
            fixture("Spread some batter onto the pan. Spread a thin layer of batter to the side of the pan. Cover "
                    + "the pan for 30-60 seconds until small air bubbles appear.",
                    "Spread some batter onto the pan", List.of(), List.of(), List.of(), false),
            fixture("Refrigerate for 1 hour or overnight. Be sure the pork chops are evenly covered.",
                    "Refrigerate for 1 hour or overnight", List.of(), List.of(), List.of(), false),
            fixture("Storing: Store in an airtight container outside of the fridge for about 2-3 days or in the "
                    + "fridge for 5 days. Freeze for up to three months.",
                    "Storing: Store in an airtight container outside of the…", List.of(), List.of(),
                    List.of(), false),
            fixture("Make the Dough: Mix flour and salt in a large bowl. Add butter, using your fingers to blend "
                    + "into a crumbly texture. Gradually add water, mixing until a dough forms. Wrap and chill "
                    + "for 30 minutes.",
                    "Make the Dough: Mix flour and salt in a large bowl", List.of("30 minutes"), List.of(1800),
                    List.of(1800), false));

    @Test
    void realSeedStepsGetSensibleTitlesAndTimers() {
        assertEquals(20, FIXTURES.size());
        for (Fixture fixture : FIXTURES) {
            List<RecipeStepTimerView> timers = RecipeStepAnalyzer.timers(fixture.text());
            assertEquals(fixture.phrases(), timers.stream().map(RecipeStepTimerView::phrase).toList(), fixture.text());
            assertEquals(fixture.seconds(), timers.stream().map(RecipeStepTimerView::seconds).toList(), fixture.text());
            assertEquals(fixture.maxSeconds(), timers.stream().map(RecipeStepTimerView::maxSeconds).toList(),
                    fixture.text());
            timers.forEach(timer -> assertEquals(fixture.eachSide(), timer.eachSide(), fixture.text()));
            String title = RecipeStepAnalyzer.title(fixture.text(), 1);
            assertEquals(fixture.title(), title, fixture.text());
            assertTrue(title.length() <= 60 && !title.isBlank(), title);
        }
    }

    @Test
    void realIngredientLinesAreMatchedToTheFirstStepThatUsesThem() {
        IngredientRef prawns = new IngredientRef(UUID.randomUUID(), "Crustaceans, shrimp, raw", "500g Raw king prawns");
        IngredientRef oil = new IngredientRef(UUID.randomUUID(), "Oil, olive", "3 tablespoons Olive oil");
        IngredientRef onion = new IngredientRef(UUID.randomUUID(), "Onions, raw", "1 Chopped onion");
        IngredientRef feta = new IngredientRef(UUID.randomUUID(), "Cheese, feta", "200g Feta");
        IngredientRef saffron = new IngredientRef(UUID.randomUUID(), "Spices, saffron", "1 pinch Saffron");
        List<List<UUID>> used = RecipeStepAnalyzer.ingredientIdsByStep(
                List.of(FIXTURES.get(0).text(), FIXTURES.get(1).text(),
                        "Add the feta and remove from the heat. Serve warm with slices of crusty bread."),
                List.of(prawns, oil, onion, feta, saffron));
        assertEquals(List.of(prawns.id()), used.get(0));
        assertEquals(List.of(oil.id(), onion.id()), used.get(1));
        assertEquals(List.of(feta.id()), used.get(2));
    }

    /** The words that must never start a short ingredient name. */
    private static final List<String> NOISE_START = List.of("chopped", "finely", "roughly", "diced", "sliced", "peeled",
            "crushed", "minced", "grated", "ground", "fresh", "freshly", "beaten", "softened", "melted", "cubed",
            "shredded", "toasted", "cooked", "drained", "rinsed", "halved", "quartered", "to serve", "to garnish",
            "for garnish", "for frying", "to taste", "a handful of", "a pinch of");

    @Test
    void noSeedIngredientNameStartsWithAPreparationOrUseWord() throws IOException {
        Path seed = Path.of("src/main/resources/db/seed/V902__themealdb_system_recipes.sql");
        Pattern line = Pattern.compile(",\\d+\\.\\d+,'((?:[^']|'')*)',(?:null|\\d+\\.\\d+),\\d+,timestamp");
        Set<String> texts = new TreeSet<>();
        for (String row : Files.readAllLines(seed, StandardCharsets.UTF_8)) {
            if (row.startsWith("('") && !row.contains("insert")) {
                Matcher match = line.matcher(row);
                if (match.find() && row.contains("'themealdb-import'")) {
                    texts.add(match.group(1).replace("''", "'"));
                }
            }
        }
        assertTrue(texts.size() > 1000, "ingredient lines found: " + texts.size());
        List<String> offenders = new ArrayList<>();
        for (String text : texts) {
            String name = RecipeStepAnalyzer.ingredientName(text);
            String lower = name.toLowerCase(Locale.ROOT);
            for (String noise : NOISE_START) {
                if (lower.equals(noise) || lower.startsWith(noise + " ")) {
                    offenders.add(text + " -> " + name);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "names that still start with a noise word: " + offenders);
    }
}
