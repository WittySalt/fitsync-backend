package com.fitsync.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param graceDays how many days after a deletion request the person can still cancel it
 * @param purge     how accounts whose grace period has ended are erased
 */
@ConfigurationProperties(prefix = "fitsync.account-deletion")
public record AccountDeletionProperties(@DefaultValue("30") int graceDays,
                                        @DefaultValue Purge purge) {

    public AccountDeletionProperties {
        if (graceDays < 0) {
            throw new IllegalArgumentException("fitsync.account-deletion.grace-days must not be negative");
        }
        if (purge == null) {
            purge = new Purge(true, "0 30 3 * * *", 100, false);
        }
    }

    /**
     * @param enabled        whether the scheduled purge runs at all (daily and once at startup)
     * @param cron           when the daily run starts, in UTC
     * @param batchSize      how many accounts one run claims and how many it finishes erasing
     * @param cognitoEnabled whether the Cognito user is deleted with {@code AdminDeleteUser}; when false the
     *                       Cognito step is skipped (local runs without AWS credentials)
     */
    public record Purge(@DefaultValue("true") boolean enabled,
                        @DefaultValue("0 30 3 * * *") String cron,
                        @DefaultValue("100") int batchSize,
                        @DefaultValue("false") boolean cognitoEnabled) {

        public Purge {
            if (batchSize < 1) {
                throw new IllegalArgumentException("fitsync.account-deletion.purge.batch-size must be at least 1");
            }
        }
    }
}
