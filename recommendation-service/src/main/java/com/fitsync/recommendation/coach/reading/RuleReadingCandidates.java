package com.fitsync.recommendation.coach.reading;

import java.util.List;
import java.util.Optional;

/**
 * The step the nutrition and progress rule writers share: try the whole body, then drop sentences
 * from the end until the validator accepts what is left.
 *
 * <p>Dropping from the end keeps the sentences a reading leads with, and it is the same idea the
 * workout writer follows with its own ordered candidates. A headline that fails on its own fails
 * every candidate, so nothing is returned in that case rather than a reading that was never checked.
 */
final class RuleReadingCandidates {

    private RuleReadingCandidates() {
    }

    static Optional<RuleReadingWriter.RuleReading> firstValid(ReadingValidator validator, String headline,
                                                              List<String> sentences, List<ReadingFact> facts) {
        for (int keep = sentences.size(); keep >= 1; keep--) {
            String body = String.join(" ", sentences.subList(0, keep));
            ReadingValidator.Result result = validator.validate(headline, body, facts);
            if (result.valid()) {
                return Optional.of(new RuleReadingWriter.RuleReading(headline, body, result.citations()));
            }
        }
        return Optional.empty();
    }

    static ReadingFact find(List<ReadingFact> facts, String key) {
        for (ReadingFact fact : facts) {
            if (key.equals(fact.key())) {
                return fact;
            }
        }
        return null;
    }

    static String cite(ReadingFact fact) {
        return "[" + fact.number() + "]";
    }
}
