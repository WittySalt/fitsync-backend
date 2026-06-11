package com.fitsync.workout.dto;

public record OutboxRelayResponse(
        int requestedBatchSize,
        int publishedCount) {
}
