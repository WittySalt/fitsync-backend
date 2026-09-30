package com.fitsync.recommendation.coach.reading;

import java.util.List;

import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** The nutrition page: protein, its split across the day, and calories, over the last full week. */
@Component
class NutritionReadingPage implements ReadingPage {

    private static final String FACTS_NOTE = " The period label tells you this covers the last full week. "
            + "A fact about protein at breakfast, or at lunch and dinner, gives its share of the day's "
            + "protein in its note.";

    private static final String EXAMPLE = """
            period: PREVIOUS_WEEK
            facts: [
              {"number":1,"label":"Protein a day","value":"118.4","unit":"g","note":"Down 5%","detail":"124.6 g the week before; target 130 g"},
              {"number":2,"label":"Protein at breakfast","value":"14.2","unit":"g","note":"12% of the day","detail":""},
              {"number":3,"label":"Protein at lunch and dinner","value":"81.7","unit":"g","note":"69% of the day","detail":""},
              {"number":4,"label":"Calories a day","value":"1,940","unit":"kcal","note":"Unchanged","detail":"1,940 kcal the week before; target 2,050 kcal"}
            ]

            Example output:
            {"headline": "Protein slipped and most of it came late in the day.", "body": "You ate 118.4 g of protein a day [1], down from 124.6 g, and 81.7 g [3] of it came at lunch and dinner. Breakfast gave 14.2 g [2], while calories stayed the same at 1,940 kcal [4]."}""";

    static final String SYSTEM_PROMPT = ReadingPrompt.compose("", ReadingPrompt.DIRECTION_RULE_END,
            FACTS_NOTE, EXAMPLE);

    private final NutritionCatalogueClient nutritionClient;
    private final NutritionFactsBuilder factsBuilder;
    // Resolved through ObjectProvider for the same SpotBugs reason as on the workout page.
    private final ObjectProvider<NutritionRuleReadingWriter> ruleReadingWriterProvider;

    NutritionReadingPage(NutritionCatalogueClient nutritionClient, NutritionFactsBuilder factsBuilder,
                         ObjectProvider<NutritionRuleReadingWriter> ruleReadingWriterProvider) {
        this.nutritionClient = nutritionClient;
        this.factsBuilder = factsBuilder;
        this.ruleReadingWriterProvider = ruleReadingWriterProvider;
    }

    @Override
    public ReadingSurface surface() {
        return ReadingSurface.NUTRITION;
    }

    @Override
    public int minimumFacts() {
        return 1;
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String unavailableMessage() {
        return "The nutrition service could not be read, so there is nothing to build today's reading from.";
    }

    @Override
    public PageSnapshot snapshot(String bearerToken, String timezone) {
        NutritionInsight insight = nutritionClient.pageInsight(bearerToken, timezone);
        if (insight == null) {
            return null;
        }
        List<ReadingFact> facts = factsBuilder.build(insight);
        return new PageSnapshot(insight.period(), insight.periodStart(), insight.periodEnd(), facts,
                List.of(), insight.isNone(),
                () -> ruleReadingWriterProvider.getObject().write(insight, facts));
    }
}
