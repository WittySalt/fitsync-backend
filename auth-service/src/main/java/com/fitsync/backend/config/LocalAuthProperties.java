package com.fitsync.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param localAccountsEnabled whether password registration and login are served; people sign in through
 *                             Cognito, so this stays off unless a local environment needs it
 */
@ConfigurationProperties(prefix = "fitsync.auth")
public record LocalAuthProperties(@DefaultValue("false") boolean localAccountsEnabled) {
}
