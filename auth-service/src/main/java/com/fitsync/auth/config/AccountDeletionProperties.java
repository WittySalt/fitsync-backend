package com.fitsync.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param graceDays how many days after a deletion request the person can still cancel it
 */
@ConfigurationProperties(prefix = "fitsync.account-deletion")
public record AccountDeletionProperties(@DefaultValue("30") int graceDays) {

    public AccountDeletionProperties {
        if (graceDays < 0) {
            throw new IllegalArgumentException("fitsync.account-deletion.grace-days must not be negative");
        }
    }
}
