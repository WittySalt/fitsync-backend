package com.fitsync.notification.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    /** One clock for every time decision, so tests can stand at any moment of any day. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
