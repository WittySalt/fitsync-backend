package com.fitsync.recommendation.coach.reading;

/**
 * The columns {@link CoachPageReadingRepository#findQualityRows} projects for the admin quality
 * report: which surface, which source wrote the reading, and what became of it. Never
 * {@code headline}, {@code body} or {@code citations}, which can quote a user's own numbers back
 * to them.
 */
public record CoachPageReadingQualityRow(ReadingSurface surface, String source, ReadingOutcome outcome) {
}
