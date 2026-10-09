package com.fitsync.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param refreshTokenTtl  how long a session stays usable without a refresh; every refresh starts it again
 *                         (sliding), so a device that is used at least this often stays signed in
 * @param reuseGracePeriod how long after a rotation the replaced refresh token is refused without revoking the
 *                         session, so a late duplicate from the same device (a second tab, a retried request)
 *                         does not sign it out; zero turns the grace off
 */
@ConfigurationProperties(prefix = "fitsync.auth.sessions")
public record SessionProperties(
        @DefaultValue("P30D") Duration refreshTokenTtl,
        @DefaultValue("PT10S") Duration reuseGracePeriod) {

    public SessionProperties {
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("fitsync.auth.sessions.refresh-token-ttl must be positive");
        }
        if (reuseGracePeriod == null || reuseGracePeriod.isNegative()) {
            throw new IllegalArgumentException("fitsync.auth.sessions.reuse-grace-period must not be negative");
        }
    }
}
