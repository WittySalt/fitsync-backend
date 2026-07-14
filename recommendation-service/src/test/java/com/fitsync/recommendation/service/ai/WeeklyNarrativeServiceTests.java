package com.fitsync.recommendation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WeeklyNarrativeServiceTests {

    @Autowired
    private WeeklyNarrativeService weeklyNarrativeService;

    @Autowired
    private RecommendationContextRepository contextRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClaudeClient claudeClient;

    private final UUID userId = UUID.randomUUID();

    @Test
    void generatesAndCachesTheAiNarrativeFromRecapFacts() throws Exception {
        persistContext();
        when(claudeClient.complete(anyString(), contains("\"workoutCount\":4")))
                .thenReturn("Strong week - four workouts across four days. Keep the streak alive next week.");

        weeklyNarrativeService.generateNow(userId);

        RecommendationResultEntity result = resultRepository
                .findByUserIdAndType(userId, RecommendationTypes.WEEKLY_NARRATIVE).orElseThrow();
        assertThat(result.getSource()).isEqualTo(RecommendationSource.AI);
        assertThat(result.getContextHash()).hasSize(64);
        assertThat(Duration.between(result.getGeneratedAt(), result.getExpiresAt()))
                .isEqualTo(Duration.ofDays(7));

        JsonNode payload = objectMapper.readTree(result.getPayload());
        assertThat(payload.get("narrative").asText()).startsWith("Strong week");
        assertThat(payload.get("facts").get("activeDays").asInt()).isEqualTo(4);
    }

    @Test
    void skipsTheModelWhileTheContextIsUnchangedAndFresh() {
        persistContext();
        when(claudeClient.complete(anyString(), anyString())).thenReturn("Narrative.");

        weeklyNarrativeService.generateNow(userId);
        weeklyNarrativeService.generateNow(userId);

        verify(claudeClient, times(1)).complete(anyString(), anyString());
    }

    @Test
    void regeneratesWhenTheContextMateriallyChanges() {
        persistContext();
        when(claudeClient.complete(anyString(), anyString())).thenReturn("Narrative.");
        weeklyNarrativeService.generateNow(userId);

        RecommendationContextEntity context = contextRepository.findById(userId).orElseThrow();
        context.setCurrentStreakDays(14);
        contextRepository.saveAndFlush(context);

        weeklyNarrativeService.generateNow(userId);

        verify(claudeClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    void apiFailureDegradesSilently() {
        persistContext();
        when(claudeClient.complete(anyString(), anyString())).thenThrow(new IllegalStateException("rate limited"));

        weeklyNarrativeService.generateNow(userId);

        assertThat(resultRepository.findByUserIdAndType(userId, RecommendationTypes.WEEKLY_NARRATIVE))
                .isEmpty();
    }

    @Test
    void nothingToPhraseMeansNoModelCall() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        contextRepository.saveAndFlush(context);

        weeklyNarrativeService.generateNow(userId);
        weeklyNarrativeService.generateNow(UUID.randomUUID());

        verify(claudeClient, times(0)).complete(anyString(), anyString());
    }

    private void persistContext() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        context.setRecentWeekStart(LocalDate.parse("2026-06-29"));
        context.setRecentWorkoutCount(4);
        context.setRecentWorkoutMinutes(180);
        context.setRecentActiveDays(4);
        context.setWorkoutDaysPerWeek(4);
        context.setCurrentStreakDays(7);
        contextRepository.saveAndFlush(context);
    }
}
