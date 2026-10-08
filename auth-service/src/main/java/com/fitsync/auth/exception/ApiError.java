package com.fitsync.auth.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error body of this service. {@code code} and {@code data} are present only on errors that a client is
 * expected to react to programmatically, so every other error serializes exactly as it always has.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> data) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
        data = data == null ? null : Map.copyOf(data);
    }

    public ApiError(Instant timestamp, int status, String error, String message, String path, List<String> details) {
        this(timestamp, status, error, message, path, details, null, null);
    }

    @Override
    public List<String> details() {
        return List.copyOf(details);
    }

    @Override
    public Map<String, String> data() {
        return data == null ? null : Map.copyOf(data);
    }
}
