package com.fitsync.recommendation.coach.reading;

import java.time.LocalDate;
import java.util.List;

/**
 * One week's review on the Progress page.
 *
 * @param weekStart the Monday the week starts on
 * @param weekEnd   the Sunday it ends on
 * @param current   true for the week still going, whose review is written "so far" and written again
 *                  when its facts change
 * @param status    READY with a headline and body, WRITING while a review is being written, NONE when
 *                  the week has fewer than two facts or could not be read
 * @param headline  present only when READY
 * @param body      present only when READY; cites facts by number in square brackets
 * @param citations the fact numbers the body cites, empty unless READY
 * @param source    RULES or MODEL when READY, null otherwise
 * @param facts     the numbered facts the review was written from, each with the card it belongs to
 */
public record WeeklyReviewView(
        LocalDate weekStart,
        LocalDate weekEnd,
        boolean current,
        WeeklyReviewStatus status,
        String headline,
        String body,
        List<Integer> citations,
        String source,
        List<ReadingFact> facts) {

    public WeeklyReviewView {
        citations = citations == null ? List.of() : List.copyOf(citations);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    @Override
    public List<Integer> citations() {
        return List.copyOf(citations);
    }

    @Override
    public List<ReadingFact> facts() {
        return List.copyOf(facts);
    }
}
