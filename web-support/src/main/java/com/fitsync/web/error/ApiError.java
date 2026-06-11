package com.fitsync.web.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details,
        String correlationId) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    @Override
    public List<String> details() {
        return List.copyOf(details);
    }
}
