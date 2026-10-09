package com.fitsync.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param refreshTokenTtl how long a session stays usable without a refresh; every refresh starts it again
 *                        (sliding), so a device that is used at least this often stays signed in
 */
@ConfigurationProperties(prefix = "fitsync.auth.sessions")
public record SessionProperties(@DefaultValue("P30D") Duration refreshTokenTtl) {

    public SessionProperties {
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("fitsync.auth.sessions.refresh-token-ttl must be positive");
        }
    }
}
