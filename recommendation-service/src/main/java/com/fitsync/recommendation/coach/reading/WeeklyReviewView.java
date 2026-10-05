package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
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
 * @param eatenTodaySoFarKcal    on the current week only: the calories logged today so far, 0 when nothing
 *                               is logged yet; null on past weeks and when the Food page could not be read
 * @param proteinTodaySoFarGrams on the current week only: the protein logged today so far, as above
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
        List<ReadingFact> facts,
        Integer eatenTodaySoFarKcal,
        BigDecimal proteinTodaySoFarGrams) {

    public WeeklyReviewView {
        citations = citations == null ? List.of() : List.copyOf(citations);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    /** A review without today's figures, as every past week is. */
    public WeeklyReviewView(LocalDate weekStart, LocalDate weekEnd, boolean current, WeeklyReviewStatus status,
                            String headline, String body, List<Integer> citations, String source,
                            List<ReadingFact> facts) {
        this(weekStart, weekEnd, current, status, headline, body, citations, source, facts, null, null);
    }

    /** The same review carrying today's figures. */
    WeeklyReviewView withToday(Today today) {
        return today == null ? this : new WeeklyReviewView(weekStart, weekEnd, current, status, headline, body,
                citations, source, facts, today.eatenKcal(), today.proteinGrams());
    }

    /** What today holds so far, read from the Food page of the current week. */
    record Today(Integer eatenKcal, BigDecimal proteinGrams) {
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
