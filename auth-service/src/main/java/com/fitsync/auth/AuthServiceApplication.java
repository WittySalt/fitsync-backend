package com.fitsync.auth;

import com.fitsync.persistence.outbox.EnableOutboxCleanup;
import com.fitsync.persistence.outbox.OutboxPublishedColumn;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableOutboxCleanup(publishedColumn = OutboxPublishedColumn.PUBLISHED_AT)
@ConfigurationPropertiesScan
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
