package com.fitsync.recommendation.coach.reading;

import org.springframework.http.HttpStatus;

import com.fitsync.web.error.ApiException;

/**
 * Thrown when the upstream page facts could not be read: workout-service was unreachable, or its
 * response could not be parsed into {@link TrainingInsight}. 503 rather than 500, because nothing on
 * this side is broken; the dependency is, and that resolves on its own once it does.
 */
public class ReadingUnavailableException extends ApiException {
    private static final long serialVersionUID = 1L;

    public ReadingUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "READING_UNAVAILABLE", message);
    }
}
