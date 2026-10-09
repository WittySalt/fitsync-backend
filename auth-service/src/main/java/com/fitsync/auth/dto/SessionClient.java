package com.fitsync.auth.dto;

/**
 * What a sign-in request says about the device it came from: the optional {@code X-Device-Label} header and the
 * {@code User-Agent}. Neither is trusted; both are reduced to a short label before they are stored.
 */
public record SessionClient(String deviceLabelHeader, String userAgent) {

    public static SessionClient unknown() {
        return new SessionClient(null, null);
    }
}
