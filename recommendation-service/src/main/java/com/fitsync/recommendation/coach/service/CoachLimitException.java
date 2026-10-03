package com.fitsync.recommendation.coach.service;

/**
 * A user has started as many coach turns as the limits allow.
 *
 * <p>Carries the one thing a client needs to say something useful, how long until a turn is
 * available again, and a sentence it can show as it is.
 */
public class CoachLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public CoachLimitException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
