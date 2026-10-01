package com.fitsync.web.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The body of every error answer. {@code data} carries structured facts a client can act on for a
 * coded error, for example the session that blocks a new one. It is left out of the JSON when
 * empty, so an error without data looks exactly as it did before the field existed.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details,
        String correlationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> data) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
        data = data == null || data.isEmpty() ? null : new LinkedHashMap<>(data);
    }

    public ApiError(Instant timestamp,
                    int status,
                    String error,
                    String code,
                    String message,
                    String path,
                    List<String> details,
                    String correlationId) {
        this(timestamp, status, error, code, message, path, details, correlationId, null);
    }

    @Override
    public List<String> details() {
        return List.copyOf(details);
    }

    @Override
    public Map<String, String> data() {
        return data == null ? null : new LinkedHashMap<>(data);
    }
}
