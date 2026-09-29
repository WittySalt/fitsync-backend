package com.fitsync.recommendation.coach.reading;

import java.time.Instant;
import java.util.List;

/** The model's observation over the facts, present only when {@link ReadingStatus#READY}. */
public record ModelReadingView(String headline, String body, List<Integer> citations, String source, Instant generatedAt) {

    public ModelReadingView {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
