package com.fitsync.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import com.fitsync.recommendation.service.rules.RecommendationTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationResolverTests {

    @Autowired
    private RecommendationResolver resolver;

    @Autowired
    private RecommendationContextRepository contextRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodayIntakeSource todayIntakeSource;

    private final UUID userId = UUID.randomUUID();

    @Test
    void staleContextDropsToTheRuleStageAndCachesPhrasedResults() throws Exception {
        persistContext();

        List<RecommendationResultEntity> results = resolver.resolve(userId);

        assertThat(results).extracting(RecommendationResultEntity::getType).containsExactly(
                RecommendationTypes.STREAK_NUDGE, RecommendationTypes.WEEKLY_ADHERENCE);
        RecommendationResultEntity adherence = results.get(1);
        assertThat(adherence.getSource()).isEqualTo(RecommendationSource.RULE);
        assertThat(adherence.getContextHash()).hasSize(64);
        assertThat(Duration.between(adherence.getGeneratedAt(), adherence.getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));

        JsonNode payload = objectMapper.readTree(adherence.getPayload());
        assertThat(payload.get("facts").get("shortfallDays").asInt()).isEqualTo(2);
        assertThat(payload.get("narrative").asText())
                .isEqualTo("You trained 2 of 4 planned days last week — 2 more gets you to target this week.");

        assertThat(contextRepository.findById(userId).orElseThrow().isStale()).isFalse();
    }

    @Test
    void freshCacheIsServedWithoutRecomputing() {
        persistContext();
        resolver.resolve(userId);

        RecommendationResultEntity cached = resultRepository
                .findByUserIdAndType(userId, RecommendationTypes.STREAK_NUDGE).orElseThrow();
        cached.setPayload("TAMPERED");
        resultRepository.saveAndFlush(cached);

        List<RecommendationResultEntity> results = resolver.resolve(userId);

        assertThat(results.get(0).getPayload()).isEqualTo("TAMPERED");
    }

    @Test
    void recomputePrunesRulesThatNoLongerFire() {
        persistContext();
        resolver.resolve(userId);

        RecommendationContextEntity context = contextRepository.findById(userId).orElseThrow();
        context.setCurrentStreakDays(null);
        context.setStale(true);
        contextRepository.saveAndFlush(context);

        List<RecommendationResultEntity> results = resolver.resolve(userId);

        assertThat(results).extracting(RecommendationResultEntity::getType)
                .containsExactly(RecommendationTypes.WEEKLY_ADHERENCE);
    }

    @Test
    void resolvesASingleTypeThroughTheSameFunnel() {
        persistContext();

        assertThat(resolver.resolve(userId, RecommendationTypes.WEEKLY_ADHERENCE)).isPresent();
        assertThat(resolver.resolve(userId, RecommendationTypes.NUTRITION_GAP)).isEmpty();
    }

    @Test
    void theNutritionGapQuotesTheDiaryReadWithTheCallersToken() throws Exception {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        context.setCalorieGoalKcal(2100);
        context.setTodayCaloriesConsumed(4910);
        contextRepository.saveAndFlush(context);
        when(todayIntakeSource.read(eq("caller-credential"), any(), any()))
                .thenReturn(new TodayIntake(LocalDate.now(java.time.ZoneOffset.UTC), 476, BigDecimal.ZERO));

        RecommendationResultEntity gap = resolver.resolveType(userId, RecommendationTypes.NUTRITION_GAP,
                "caller-credential").orElseThrow();

        JsonNode facts = objectMapper.readTree(gap.getPayload()).get("facts");
        assertThat(facts.get("consumedCalories").asInt()).isEqualTo(476);
        assertThat(facts.get("remainingCalories").asInt()).isEqualTo(1624);
    }

    @Test
    void unknownUserResolvesToNothing() {
        assertThat(resolver.resolve(UUID.randomUUID())).isEmpty();
    }

    private void persistContext() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        context.setWorkoutDaysPerWeek(4);
        context.setRecentActiveDays(2);
        context.setRecentWorkoutCount(2);
        context.setCurrentStreakDays(7);
        contextRepository.saveAndFlush(context);
    }
}
