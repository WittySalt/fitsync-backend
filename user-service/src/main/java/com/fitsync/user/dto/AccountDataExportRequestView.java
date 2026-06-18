package com.fitsync.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.user.domain.AccountDataExportStatus;

public record AccountDataExportRequestView(
        UUID exportRequestId,
        AccountDataExportStatus status,
        Instant requestedAt,
        Instant completedAt,
        boolean downloadAvailable) {
}
