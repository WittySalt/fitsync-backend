package com.fitsync.persistence.outbox;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Turns on {@link OutboxCleanupJob} for a service that owns an {@code outbox_events} table. It is opt-in, so a
 * service that only shares this module (and has no outbox) never runs a delete against a missing table. The
 * service also needs {@code @EnableScheduling}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(OutboxCleanupConfiguration.class)
public @interface EnableOutboxCleanup {

    /** The column of this service's outbox table that holds the publish time. */
    OutboxPublishedColumn publishedColumn() default OutboxPublishedColumn.DATE_TIME_PUBLISHED;
}
