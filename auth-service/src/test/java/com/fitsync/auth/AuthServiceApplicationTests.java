package com.fitsync.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitsync.persistence.outbox.OutboxCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;

@SpringBootTest
class AuthServiceApplicationTests {

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

    @Test
    void resolvesTheClientAddressOnlyThroughTrustedProxies() {
        // "native" makes Tomcat's RemoteIpValve read X-Forwarded-For from private-range proxies only, which is
        // what the active-session view relies on when it reports request.getRemoteAddr(). The test classpath
        // has its own application.yml, so the deployed one is read from the module's sources.
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        assertThat(yaml.getObject()).containsEntry("server.forward-headers-strategy", "native");
    }

    @Autowired
    private OutboxCleanupJob outboxCleanupJob;

    @Test
    void outboxCleanupRunsAgainstThisServicesOutboxTable() {
        // A wrong column or table name would fail here as a SQL error rather than in production.
        assertThat(outboxCleanupJob.deleteExpiredPublishedEvents()).isZero();
    }
}
