package com.fitsync.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class OutboxCleanupJobTests {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTable() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
                create table outbox_events (
                    event_id uuid primary key,
                    status varchar(30) not null,
                    payload text not null,
                    date_time_created timestamp with time zone not null,
                    date_time_published timestamp with time zone)""");
    }

    @AfterEach
    void dropDatabase() {
        database.shutdown();
    }

    @Test
    void deletesOnlyPublishedEventsOlderThanTheRetention() {
        UUID oldPublished = insert("PUBLISHED", NOW.minus(Duration.ofDays(8)));
        UUID freshPublished = insert("PUBLISHED", NOW.minus(Duration.ofDays(6)));
        UUID oldPending = insert("PENDING", null);
        UUID oldFailed = insert("FAILED", null);
        UUID failedWithStaleTime = insert("FAILED", NOW.minus(Duration.ofDays(30)));

        int deleted = job(properties(Duration.ofDays(7), 500, 100)).deleteExpiredPublishedEvents();

        assertThat(deleted).isEqualTo(1);
        assertThat(remainingIds())
                .containsExactlyInAnyOrder(freshPublished, oldPending, oldFailed, failedWithStaleTime)
                .doesNotContain(oldPublished);
    }

    @Test
    void worksThroughABacklogInBatches() {
        for (int i = 0; i < 7; i++) {
            insert("PUBLISHED", NOW.minus(Duration.ofDays(10)).plusSeconds(i));
        }
        UUID pending = insert("PENDING", null);

        int deleted = job(properties(Duration.ofDays(7), 3, 100)).deleteExpiredPublishedEvents();

        assertThat(deleted).isEqualTo(7);
        assertThat(remainingIds()).containsExactly(pending);
    }

    @Test
    void stopsAfterTheBatchLimitAndLeavesTheRestForTheNextRun() {
        for (int i = 0; i < 7; i++) {
            insert("PUBLISHED", NOW.minus(Duration.ofDays(10)).plusSeconds(i));
        }

        OutboxCleanupJob job = job(properties(Duration.ofDays(7), 2, 2));

        assertThat(job.deleteExpiredPublishedEvents()).isEqualTo(4);
        assertThat(remainingIds()).hasSize(3);
        assertThat(job.deleteExpiredPublishedEvents()).isEqualTo(3);
        assertThat(remainingIds()).isEmpty();
    }

    @Test
    void aMissingTableIsLoggedNotThrownSoTheSchedulerKeepsRunning() {
        jdbcTemplate.execute("drop table outbox_events");

        job(properties(Duration.ofDays(7), 500, 100)).runSafely();
    }

    @Test
    void propertiesFallBackToSafeValues() {
        OutboxCleanupProperties props = new OutboxCleanupProperties(
                true, Duration.ofMinutes(1), 0, 0, Duration.ofSeconds(1), Duration.ofSeconds(-1));

        assertThat(props.retention()).isEqualTo(Duration.ofDays(7));
        assertThat(props.batchSize()).isEqualTo(500);
        assertThat(props.maxBatchesPerRun()).isEqualTo(100);
        assertThat(props.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(props.initialDelay()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void isRegisteredByDefaultWithASevenDayRetentionAndCanBeTurnedOff() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class))
                .withUserConfiguration(EnabledService.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:cleanup-" + UUID.randomUUID());

        runner.run(context -> {
            assertThat(context).hasSingleBean(OutboxCleanupJob.class);
            assertThat(context.getBean(OutboxCleanupProperties.class).retention()).isEqualTo(Duration.ofDays(7));
        });
        runner.withPropertyValues("fitsync.outbox.cleanup.retention=P30D")
                .run(context -> assertThat(context.getBean(OutboxCleanupProperties.class).retention())
                        .isEqualTo(Duration.ofDays(30)));
        runner.withPropertyValues("fitsync.outbox.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OutboxCleanupJob.class));
    }

    @Test
    void theAuthServiceLayoutCleansByPublishedAt() {
        jdbcTemplate.execute("drop table outbox_events");
        jdbcTemplate.execute("""
                create table outbox_events (
                    event_id uuid primary key,
                    status varchar(30) not null,
                    published_at timestamp with time zone)""");
        UUID old = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        jdbcTemplate.update("insert into outbox_events values (?, 'PUBLISHED', ?)", old,
                Timestamp.from(NOW.minus(Duration.ofDays(9))));
        jdbcTemplate.update("insert into outbox_events values (?, 'PENDING', null)", pending);

        int deleted = new OutboxCleanupJob(jdbcProvider(), properties(Duration.ofDays(7), 500, 100), CLOCK,
                OutboxPublishedColumn.PUBLISHED_AT).deleteExpiredPublishedEvents();

        assertThat(deleted).isEqualTo(1);
        assertThat(remainingIds()).containsExactly(pending);
    }

    @Test
    void theAnnotationChoosesThePublishedColumn() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class))
                .withUserConfiguration(AuthLikeService.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:cleanup-" + UUID.randomUUID())
                .run(context -> assertThat(context.getBean(OutboxCleanupJob.class))
                        .extracting("publishedColumn").isEqualTo(OutboxPublishedColumn.PUBLISHED_AT));
    }

    @EnableOutboxCleanup
    static class EnabledService {
    }

    @EnableOutboxCleanup(publishedColumn = OutboxPublishedColumn.PUBLISHED_AT)
    static class AuthLikeService {
    }

    private OutboxCleanupJob job(OutboxCleanupProperties properties) {
        return new OutboxCleanupJob(jdbcProvider(), properties, CLOCK, OutboxPublishedColumn.DATE_TIME_PUBLISHED);
    }

    private ObjectProvider<JdbcTemplate> jdbcProvider() {
        return new StaticListableBeanFactory(Map.of("jdbcTemplate", jdbcTemplate))
                .getBeanProvider(JdbcTemplate.class);
    }

    private static OutboxCleanupProperties properties(Duration retention, int batchSize, int maxBatches) {
        return new OutboxCleanupProperties(true, retention, batchSize, maxBatches, Duration.ofHours(1),
                Duration.ofMinutes(5));
    }

    private UUID insert(String status, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into outbox_events (event_id, status, payload, date_time_created, date_time_published) "
                        + "values (?, ?, ?, ?, ?)",
                id, status, "{\"weightKg\":72.5}", Timestamp.from(NOW.minus(Duration.ofDays(40))),
                publishedAt == null ? null : Timestamp.from(publishedAt));
        return id;
    }

    private List<UUID> remainingIds() {
        return jdbcTemplate.queryForList("select event_id from outbox_events", UUID.class);
    }
}
