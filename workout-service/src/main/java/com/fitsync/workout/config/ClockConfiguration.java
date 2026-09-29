package com.fitsync.workout.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as a bean so time-dependent services (e.g. weekly training insight
 * "today" resolution) can be driven by a fixed Clock in tests instead of calling Instant.now()
 * directly.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
