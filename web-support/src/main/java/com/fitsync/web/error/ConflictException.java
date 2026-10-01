package com.fitsync.web.error;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    /** A conflict a client can tell apart by its own code, with the facts it needs to resolve it. */
    public ConflictException(String code, String message, Map<String, String> data) {
        super(HttpStatus.CONFLICT, code, message, List.of(), data);
    }
}
