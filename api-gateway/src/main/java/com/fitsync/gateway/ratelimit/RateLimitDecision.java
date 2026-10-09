package com.fitsync.gateway.ratelimit;

/**
 * The answer for one request. {@code counted} is false when Redis could not be asked and the request was let
 * through unchecked; {@code remaining} and {@code retryAfterSeconds} only mean something when it is true.
 */
public record RateLimitDecision(boolean allowed, boolean counted, int limit, long remaining, long retryAfterSeconds) {

    static RateLimitDecision allowed(int limit, long remaining) {
        return new RateLimitDecision(true, true, limit, Math.max(remaining, 0), 0);
    }

    static RateLimitDecision denied(int limit, long retryAfterSeconds) {
        return new RateLimitDecision(false, true, limit, 0, Math.max(retryAfterSeconds, 1));
    }

    static RateLimitDecision unchecked(int limit) {
        return new RateLimitDecision(true, false, limit, 0, 0);
    }
}
