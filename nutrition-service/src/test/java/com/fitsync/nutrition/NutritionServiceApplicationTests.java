package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NutritionServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesDedicatedDatabaseSchema() {
        assertThat(environment.getProperty("spring.datasource.hikari.schema")).isEqualTo("nutrition");
        assertThat(environment.getProperty("spring.flyway.default-schema")).isEqualTo("nutrition");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("nutrition");
        assertThat(environment.getProperty("spring.flyway.table")).isEqualTo("nutrition_flyway_schema_history");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("nutrition");
    }
}
