package com.fitsync.web.error;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    /** Caps a caller-supplied value so an oversized header cannot flood the log. */
    private static final int MAX_LOGGED_VALUE_LENGTH = 200;

    /** Anything that could end a log line or shift a column, replaced rather than dropped. */
    private static final String CONTROL_CHARACTERS = "[\\r\\n\\t\\f\\u0000-\\u001F\\u007F]";

    private final ApiErrorFactory apiErrorFactory;

    public GlobalApiExceptionHandler(ApiErrorFactory apiErrorFactory) {
        this.apiErrorFactory = apiErrorFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", request, details);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(apiErrorFactory.create(
                ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails(), ex.getData()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Invalid request"
                : ex.getMessage();
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, List.of(message));
    }

    /** Two writes to the same row at once: the later one is told to reload and try again. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "This was changed at the same time elsewhere. Reload and try again.", request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex,
                                                        HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = "Invalid value for parameter: " + ex.getName();
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, List.of(message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        String message = "Missing required parameter: " + ex.getParameterName();
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, List.of(message));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest request) {
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Media type is not supported", request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                                                                HttpServletRequest request) {
        String message = "Uploaded file is too large";
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, List.of(message));
    }

    /**
     * A request for a path no controller maps is a client mistake, not a server failure. Without
     * these two handlers the catch-all below swallows Spring's no-handler exceptions and reports
     * 404s as 500s, which breaks the API contract and inflates the 5xx error rate with bad client
     * paths. Which of the two is raised depends on whether static resource handling is active, so
     * both are mapped.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex,
                                                    HttpServletRequest request) {
        LOG.debug("No handler for {} {}", safeForLog(request.getMethod()), safeForLog(request.getRequestURI()));
        return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", "Requested resource was not found", request, List.of());
    }

    /**
     * Last resort. This MUST log: the response carries a correlation id so a user can quote it in a
     * report, and that id is worthless unless the same id appears in a log line next to the stack
     * trace. The correlation id is set by the gateway, which generates one when the caller does not
     * supply it, so every request arriving through the normal path carries one.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnhandled(Exception ex,
                                                    HttpServletRequest request) {
        ResponseEntity<ApiError> response = buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request, List.of());
        ApiError body = response.getBody();
        LOG.error("Unhandled exception [correlationId={}] {} {}",
                safeForLog(body == null ? null : body.correlationId()),
                safeForLog(request.getMethod()),
                safeForLog(request.getRequestURI()),
                ex);
        return response;
    }

    /**
     * The request path and the correlation-id header are caller-controlled, and a caller that puts
     * a line break in either can forge whole log lines. SpotBugs flagged exactly this
     * (CRLF_INJECTION_LOGS) on the first version of the logging added here, which is the gate doing
     * its job. Control characters are replaced rather than dropped so the tampering stays visible,
     * and the value is truncated so an oversized header cannot flood the log.
     */
    private static String safeForLog(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.length() > MAX_LOGGED_VALUE_LENGTH
                ? value.substring(0, MAX_LOGGED_VALUE_LENGTH) + "..."
                : value;
        return trimmed.replaceAll(CONTROL_CHARACTERS, "_");
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status,
                                                String code,
                                                String message,
                                                HttpServletRequest request,
                                                List<String> details) {
        return ResponseEntity.status(status).body(apiErrorFactory.create(status, code, message, request, details));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
