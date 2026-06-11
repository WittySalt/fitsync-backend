package com.fitsync.gateway.exception;

import java.time.Instant;

import com.fitsync.gateway.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiError> handleDownstreamServiceException(DownstreamServiceException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "DOWNSTREAM_UNAVAILABLE",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now(),
                        request.getHeader("X-Correlation-Id")));
    }
}
