package com.fitsync.web.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> details;
    private final LinkedHashMap<String, String> data;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details) {
        this(status, code, message, details, Map.of());
    }

    /** An error that also carries structured data for the client, such as the record that blocks the request. */
    public ApiException(HttpStatus status, String code, String message, List<String> details, Map<String, String> data) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
        this.data = new LinkedHashMap<>(data);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }

    public Map<String, String> getData() {
        return new LinkedHashMap<>(data);
    }
}
