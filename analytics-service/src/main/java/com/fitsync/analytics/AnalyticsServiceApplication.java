package com.fitsync.analytics;

import com.fitsync.analytics.config.AnalyticsProperties;
import com.fitsync.analytics.config.JwtProperties;
import com.fitsync.analytics.messaging.OutboxRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.fitsync.analytics", "com.fitsync.web"})
@EnableConfigurationProperties({JwtProperties.class, AnalyticsProperties.class, OutboxRelayProperties.class})
@EnableScheduling
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
