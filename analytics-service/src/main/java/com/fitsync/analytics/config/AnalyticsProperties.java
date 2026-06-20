package com.fitsync.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable analytics configuration bound via constructor binding, mirroring {@link JwtProperties}.
 * Records keep the properties immutable so SpotBugs does not flag EI_EXPOSE_REP on the accessors.
 */
@ConfigurationProperties(prefix = "fitsync.analytics")
public record AnalyticsProperties(@DefaultValue Trends trends) {

    public record Trends(@DefaultValue MaxRangeDays maxRangeDays) {
    }

    public record MaxRangeDays(
            @DefaultValue("370") int day,
            @DefaultValue("1095") int week,
            @DefaultValue("1830") int month) {
    }
}
