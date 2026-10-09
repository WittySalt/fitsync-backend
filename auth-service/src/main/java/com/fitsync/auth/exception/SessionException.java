package com.fitsync.auth.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A refresh or session call refused with 401 and a code the client signs out on: {@link #EXPIRED} when the
 * session is unknown or ran out, {@link #REVOKED} when it was revoked (including on refresh-token reuse).
 */
public class SessionException extends CodedApiException {

    public static final String EXPIRED = "SESSION_EXPIRED";
    public static final String REVOKED = "SESSION_REVOKED";

    private SessionException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message, Map.of());
    }

    public static SessionException expired() {
        return new SessionException(EXPIRED, "The session has expired; sign in again");
    }

    public static SessionException revoked() {
        return new SessionException(REVOKED, "The session has been revoked; sign in again");
    }
}
