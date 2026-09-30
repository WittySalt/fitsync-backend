package com.fitsync.nutrition.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as a bean so services that need today's date in the caller's timezone
 * can be driven by a fixed clock in tests instead of reading the wall clock directly.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
