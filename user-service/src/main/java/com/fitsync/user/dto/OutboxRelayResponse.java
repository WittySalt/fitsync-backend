package com.fitsync.user.dto;

public record OutboxRelayResponse(
        int requestedBatchSize,
        int publishedCount
) {
}
