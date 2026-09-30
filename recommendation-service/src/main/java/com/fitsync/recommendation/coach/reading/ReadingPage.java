package com.fitsync.recommendation.coach.reading;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What differs between the pages that show a reading: where the facts come from, how they are built,
 * how the default reading is written and what the model is told about the page.
 *
 * <p>{@link CoachReadingService} holds everything the pages share, which is the stored reading, the
 * background generation, the validation and the response, and asks the page only for these four
 * things. A page never touches the store or the model.
 */
interface ReadingPage {

    ReadingSurface surface();

    /** How many facts a period needs before there is anything to read. */
    int minimumFacts();

    /** The complete system prompt the model is given for this page. */
    String systemPrompt();

    /** What the 503 says when the page's upstream cannot be read. */
    String unavailableMessage();

    /**
     * Reads the upstream and builds the facts.
     *
     * @return the snapshot, or null when the page's own upstream could not be read at all
     */
    PageSnapshot snapshot(String bearerToken, String timezone);

    /**
     * One page's facts as of one request, with a way to write the default reading over them.
     *
     * @param nothingToRead true when the upstream says there is no period worth reading
     * @param lifts         workout only; every other page passes an empty list
     * @param ruleReading   writes the default reading, evaluated only when it is needed
     */
    record PageSnapshot(String period, LocalDate periodStart, LocalDate periodEnd, List<ReadingFact> facts,
                        List<TrainingInsight.LiftProgress> lifts, boolean nothingToRead,
                        Supplier<Optional<RuleReadingWriter.RuleReading>> ruleReading) {

        public PageSnapshot {
            facts = facts == null ? List.of() : List.copyOf(facts);
            lifts = lifts == null ? List.of() : List.copyOf(lifts);
        }

        @Override
        public List<ReadingFact> facts() {
            return List.copyOf(facts);
        }

        @Override
        public List<TrainingInsight.LiftProgress> lifts() {
            return List.copyOf(lifts);
        }
    }
}
