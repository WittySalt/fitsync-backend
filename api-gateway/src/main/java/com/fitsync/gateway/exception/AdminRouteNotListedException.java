package com.fitsync.gateway.exception;

/** An admin console request named an internal path that is not on the gateway's list of forwardable paths. */
public class AdminRouteNotListedException extends RuntimeException {

    public AdminRouteNotListedException(String message) {
        super(message);
    }
}
