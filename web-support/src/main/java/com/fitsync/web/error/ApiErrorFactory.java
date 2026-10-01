package com.fitsync.web.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public class ApiErrorFactory {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    public ApiError create(HttpStatus status,
                           String code,
                           String message,
                           HttpServletRequest request,
                           List<String> details) {
        return create(status, code, message, request, details, null);
    }

    public ApiError create(HttpStatus status,
                           String code,
                           String message,
                           HttpServletRequest request,
                           List<String> details,
                           Map<String, String> data) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                List.copyOf(details),
                request.getHeader(CORRELATION_ID_HEADER),
                data);
    }
}
