package com.fitsync.auth.dto;

public record OutboxRelayResponse(
        int requestedBatchSize,
        int publishedCount
) {
}
