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
        return buildError(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Invalid request"
                : ex.getMessage();
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, List.of(message));
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
        LOG.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
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
                body == null ? null : body.correlationId(),
                request.getMethod(),
                request.getRequestURI(),
                ex);
        return response;
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
