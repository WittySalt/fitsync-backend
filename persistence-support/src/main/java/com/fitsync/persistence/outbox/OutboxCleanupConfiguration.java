package com.fitsync.persistence.outbox;

import java.time.Clock;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/** Registers the outbox cleanup for services annotated with {@link EnableOutboxCleanup}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxCleanupProperties.class)
public class OutboxCleanupConfiguration implements ImportAware {

    private OutboxPublishedColumn publishedColumn = OutboxPublishedColumn.DATE_TIME_PUBLISHED;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> attributes = importMetadata.getAnnotationAttributes(EnableOutboxCleanup.class.getName());
        if (attributes != null && attributes.get("publishedColumn") instanceof OutboxPublishedColumn column) {
            this.publishedColumn = column;
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.outbox.cleanup", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    OutboxCleanupJob outboxCleanupJob(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                      OutboxCleanupProperties properties) {
        return new OutboxCleanupJob(jdbcTemplateProvider, properties, Clock.systemUTC(), publishedColumn);
    }
}
