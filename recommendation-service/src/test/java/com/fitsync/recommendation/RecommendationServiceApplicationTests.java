package com.fitsync.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fitsync.recommendation.service.ai.ClaudeClient;
import com.fitsync.recommendation.service.rules.RecommendationTypes;
import com.fitsync.recommendation.service.rules.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private List<Rule> rules;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void usesDedicatedDatabaseSchema() {
        assertThat(environment.getProperty("spring.datasource.hikari.schema")).isEqualTo("recommendation");
        assertThat(environment.getProperty("spring.flyway.default-schema")).isEqualTo("recommendation");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("recommendation");
        assertThat(environment.getProperty("spring.flyway.table"))
                .isEqualTo("recommendation_flyway_schema_history");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema"))
                .isEqualTo("recommendation");
    }

    @Test
    void aiTierIsOffByDefaultSoTheServiceRunsOnRulesAlone() {
        assertThat(applicationContext.getBeanProvider(ClaudeClient.class).getIfAvailable()).isNull();
    }

    @Test
    void registersAllSevenLocalRules() {
        assertThat(rules).extracting(Rule::type).containsExactlyInAnyOrder(
                RecommendationTypes.NUTRITION_GAP,
                RecommendationTypes.WEEKLY_ADHERENCE,
                RecommendationTypes.GOAL_PACE,
                RecommendationTypes.STREAK_NUDGE,
                RecommendationTypes.COLD_START,
                RecommendationTypes.PERSONAL_RECORD,
                RecommendationTypes.LOGGING_REMINDER);
    }
}
