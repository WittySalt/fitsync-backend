package com.fitsync.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitsync.persistence.outbox.OutboxCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkoutServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesDedicatedDatabaseSchema() {
        assertThat(environment.getProperty("spring.datasource.hikari.schema")).isEqualTo("workout");
        assertThat(environment.getProperty("spring.flyway.default-schema")).isEqualTo("workout");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("workout");
        assertThat(environment.getProperty("spring.flyway.table")).isEqualTo("workout_flyway_schema_history");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("workout");
    }

    @Autowired
    private OutboxCleanupJob outboxCleanupJob;

    @Test
    void outboxCleanupRunsAgainstThisServicesOutboxTable() {
        // A wrong column or table name would fail here as a SQL error rather than in production.
        assertThat(outboxCleanupJob.deleteExpiredPublishedEvents()).isZero();
    }
}
