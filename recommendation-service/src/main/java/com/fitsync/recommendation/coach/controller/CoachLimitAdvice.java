package com.fitsync.recommendation.coach.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fitsync.recommendation.coach.service.CoachLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a full limit into a 429 whose body is small and fixed, so the client can show the message
 * as it is and count down {@code retryAfterSeconds}: {@code {code, retryAfterSeconds, message}}.
 * The standard {@code Retry-After} header carries the same number for anything that is not the app.
 */
@RestControllerAdvice(assignableTypes = {CoachThreadController.class, SessionDraftController.class})
public class CoachLimitAdvice {

    static final String CODE = "COACH_LIMIT";

    @ExceptionHandler(CoachLimitException.class)
    public ResponseEntity<Map<String, Object>> limited(CoachLimitException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", CODE);
        body.put("retryAfterSeconds", ex.retryAfterSeconds());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(body);
    }
}
