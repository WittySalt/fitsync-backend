package com.fitsync.nutrition.dto;

public record OutboxRelayResponse(
        int requestedBatchSize,
        int publishedCount) {
}
