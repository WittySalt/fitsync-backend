package com.fitsync.recommendation.coach.reading;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.client.UserProfileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The progress page: weight, the weekly rate, the goal and, when nutrition can supply them, calories,
 * over the 28 days up to the reference date.
 *
 * <p>The calories come from a second upstream, and that call is allowed to fail. A reading about
 * weight is still worth showing without a calorie fact, so a failure there only leaves the fact out.
 */
@Component
class ProgressReadingPage implements ReadingPage {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressReadingPage.class);

    private static final String FACTS_NOTE = " The period label tells you this covers the last 28 days. "
            + "A fact labelled Change a week gives how far the weight moved in a week, and its note says "
            + "whether the weight went Down or Up. A fact labelled Goal gives the goal weight and, in its "
            + "detail, the date this rate reaches it.";

    private static final String EXAMPLE = """
            period: LAST_28_DAYS
            facts: [
              {"number":1,"label":"Weight","value":"83.6","unit":"kg","note":"3 Mar","detail":"9 weigh-ins in eight weeks"},
              {"number":2,"label":"Change a week","value":"0.35","unit":"kg","note":"Down","detail":"over the last 28 days"},
              {"number":3,"label":"Goal","value":"80","unit":"kg","note":"by 30 Apr","detail":"12 Apr at this rate"}
            ]

            Example output:
            {"headline": "At this rate you reach your goal a little early.", "body": "You weighed 83.6 kg on 3 Mar [1], which is down 0.35 kg a week [2]. Your goal is 80 kg by 30 Apr [3], and this rate gets there on 12 Apr [3]."}""";

    static final String SYSTEM_PROMPT = ReadingPrompt.compose("", ReadingPrompt.DIRECTION_RULE_END,
            FACTS_NOTE, EXAMPLE);

    private final UserProfileClient userClient;
    private final NutritionCatalogueClient nutritionClient;
    private final ProgressFactsBuilder factsBuilder;
    // Resolved through ObjectProvider for the same SpotBugs reason as on the workout page.
    private final ObjectProvider<ProgressRuleReadingWriter> ruleReadingWriterProvider;

    ProgressReadingPage(UserProfileClient userClient, NutritionCatalogueClient nutritionClient,
                        ProgressFactsBuilder factsBuilder,
                        ObjectProvider<ProgressRuleReadingWriter> ruleReadingWriterProvider) {
        this.userClient = userClient;
        this.nutritionClient = nutritionClient;
        this.factsBuilder = factsBuilder;
        this.ruleReadingWriterProvider = ruleReadingWriterProvider;
    }

    @Override
    public ReadingSurface surface() {
        return ReadingSurface.PROGRESS;
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
        return "The user service could not be read, so there is nothing to build today's reading from.";
    }

    @Override
    public PageSnapshot snapshot(String bearerToken, String timezone) {
        ProgressInsight insight = userClient.progressInsight(bearerToken, timezone);
        if (insight == null) {
            return null;
        }
        if (insight.weightKg() == null) {
            return new PageSnapshot(ProgressInsight.PERIOD, periodStart(insight), insight.referenceDate(),
                    List.of(), List.of(), true, Optional::empty);
        }
        List<ReadingFact> facts = factsBuilder.build(insight, calorieInsight(bearerToken, timezone));
        return new PageSnapshot(ProgressInsight.PERIOD, periodStart(insight), insight.referenceDate(), facts,
                List.of(), false, () -> ruleReadingWriterProvider.getObject().write(insight, facts));
    }

    /** The first day of the 28 days that end on the reference date, itself included. */
    private static LocalDate periodStart(ProgressInsight insight) {
        LocalDate reference = insight.referenceDate();
        return reference == null ? null : reference.minusDays(ProgressInsight.PERIOD_DAYS - 1L);
    }

    /** The nutrition insight for the calorie fact, or null when it cannot be had. */
    private NutritionInsight calorieInsight(String bearerToken, String timezone) {
        try {
            return nutritionClient.pageInsight(bearerToken, timezone);
        } catch (RuntimeException ex) {
            LOG.warn("event=progress_reading_calories_unavailable reason={}",
                    String.valueOf(ex.getClass().getSimpleName()).replace("\r", "_").replace("\n", "_"));
            return null;
        }
    }
}
