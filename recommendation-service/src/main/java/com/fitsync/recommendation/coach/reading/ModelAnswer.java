package com.fitsync.recommendation.coach.reading;

/** The model's raw answer to a reading prompt, before validation. */
public record ModelAnswer(String headline, String body) {
}
