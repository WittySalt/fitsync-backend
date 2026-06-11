package com.fitsync.backend.dto;

public record OutboxRelayResponse(
        int requestedBatchSize,
        int publishedCount
) {
}
