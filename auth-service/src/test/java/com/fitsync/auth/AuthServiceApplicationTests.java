package com.fitsync.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class FitSyncBackendApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesDedicatedDatabaseSchema() {
        assertThat(environment.getProperty("spring.datasource.hikari.schema")).isEqualTo("auth");
        assertThat(environment.getProperty("spring.flyway.default-schema")).isEqualTo("auth");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("auth");
        assertThat(environment.getProperty("spring.flyway.table")).isEqualTo("auth_flyway_schema_history");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("auth");
    }
}
