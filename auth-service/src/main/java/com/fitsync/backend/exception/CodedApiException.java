package com.fitsync.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * An error that carries a machine-readable code, and optionally structured data, for clients that branch on it.
 */
public class CodedApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final LinkedHashMap<String, String> data;

    public CodedApiException(HttpStatus status, String code, String message, Map<String, String> data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = new LinkedHashMap<>(data);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getData() {
        return Map.copyOf(data);
    }
}
