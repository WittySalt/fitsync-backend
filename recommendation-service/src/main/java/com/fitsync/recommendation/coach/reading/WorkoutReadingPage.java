package com.fitsync.recommendation.coach.reading;

import java.util.List;

import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** The workout page: four training facts over the current week so far, Monday to today. */
@Component
class WorkoutReadingPage implements ReadingPage {

    private static final String FACTS_NOTE = " The period label tells you this covers the current week so far, "
            + "Monday to today. A fact labelled Pace gives minutes and seconds for one kilometre, "
            + "so a lower pace is a faster one; its note says how many seconds faster or slower it was.";

    private static final String EXAMPLE = """
            period: THIS_WEEK
            facts: [
              {"number":1,"label":"Estimated best single, Bench Press","value":"30","unit":"kg","note":"Up 12%","detail":"26.8 kg four weeks before"},
              {"number":2,"label":"Session length","value":"45","unit":"min","note":"Down 10 min","detail":"55 min the week before"},
              {"number":3,"label":"Barbell Deadlift record","value":"80","unit":"kg","note":"Thursday","detail":"75 kg before"},
              {"number":4,"label":"Sessions with leg work","value":"3","unit":"of 5","note":"","detail":"Back Squat"}
            ]

            Example output:
            {"headline": "Your bench press best single rose in shorter sessions.", "body": "Your bench press best single rose 12% to 30 kg [1]. Sessions took 45 min [2], 10 min shorter than the week before. Thursday brought a deadlift of 80 kg [3], up from 75 kg [3]."}""";

    static final String SYSTEM_PROMPT = ReadingPrompt.compose(ReadingPrompt.RECORD_RULE,
            ReadingPrompt.DIRECTION_RULE_END_WITH_RECORDS, FACTS_NOTE, EXAMPLE);

    private final WorkoutCatalogueClient workoutClient;
    // The builder is pure and holds no state, so the page keeps its own.
    private final ReadingFactsBuilder factsBuilder = new ReadingFactsBuilder();
    // Resolved through ObjectProvider rather than stored directly, the same fix
    // SpringKafkaEventProducer uses elsewhere in this codebase, because SpotBugs' EI_EXPOSE_REP2
    // flags a constructor-injected singleton bean stored as a plain field regardless of whether the
    // bean itself is stateless.
    private final ObjectProvider<RuleReadingWriter> ruleReadingWriterProvider;

    WorkoutReadingPage(WorkoutCatalogueClient workoutClient, ObjectProvider<RuleReadingWriter> ruleReadingWriterProvider) {
        this.workoutClient = workoutClient;
        this.ruleReadingWriterProvider = ruleReadingWriterProvider;
    }

    @Override
    public ReadingSurface surface() {
        return ReadingSurface.WORKOUT;
    }

    @Override
    public int minimumFacts() {
        return 2;
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String unavailableMessage() {
        return "The workout service could not be read, so there is nothing to build today's reading from.";
    }

    @Override
    public PageSnapshot snapshot(String bearerToken, String timezone) {
        TrainingInsight read = workoutClient.trainingInsight(bearerToken, timezone);
        if (read == null) {
            return null;
        }
        // Strength is read as the estimated best single; the weekly insight carries the main lifts' figures.
        List<TrainingInsight.BestSingle> singles = workoutClient.weeklyBestSingles(bearerToken, timezone);
        TrainingInsight insight = singles == null || singles.isEmpty() ? read : read.withBestSingles(singles);
        List<ReadingFact> facts = factsBuilder.build(insight);
        return new PageSnapshot(insight.period(), insight.periodStart(), insight.periodEnd(), facts,
                insight.lifts(), TrainingInsight.PERIOD_NONE.equals(insight.period()),
                () -> ruleReadingWriterProvider.getObject().write(insight, facts));
    }
}
