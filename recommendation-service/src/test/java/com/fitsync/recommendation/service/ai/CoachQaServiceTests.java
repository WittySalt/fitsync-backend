package com.fitsync.recommendation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.config.RecommendationProperties;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.web.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "fitsync.recommendation.ai.coach-questions-per-hour=2")
@ActiveProfiles("test")
@Transactional
class CoachQaServiceTests {

    @Autowired
    private CoachQaService coachQaService;

    @Autowired
    private RecommendationContextRepository contextRepository;

    @MockBean
    private ClaudeClient claudeClient;

    private final UUID userId = UUID.randomUUID();

    @Test
    void answersGroundedOnTheUsersFactsAndQuestion() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        context.setWorkoutDaysPerWeek(4);
        context.setRecentActiveDays(4);
        contextRepository.saveAndFlush(context);
        when(claudeClient.complete(anyString(), contains("\"status\":\"MET\"")))
                .thenReturn("All 4 planned days done - a fully on-target week.");

        String answer = coachQaService.ask(userId, "How is my training week going?");

        assertThat(answer).isEqualTo("All 4 planned days done - a fully on-target week.");
    }

    @Test
    void enforcesTheHourlyQuestionBudget() {
        when(claudeClient.complete(anyString(), anyString())).thenReturn("Answer.");
        coachQaService.ask(userId, "One?");
        coachQaService.ask(userId, "Two?");

        assertThatThrownBy(() -> coachQaService.ask(userId, "Three?"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getCode()).isEqualTo("COACH_RATE_LIMITED");
                });
    }

    @Test
    void modelFailureBecomesAFriendlyServiceUnavailable() {
        when(claudeClient.complete(anyString(), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> coachQaService.ask(userId, "Hello?"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("COACH_UNAVAILABLE");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledAiTierMeansNoCoach() {
        ObjectProvider<ClaudeClient> emptyProvider = mock(ObjectProvider.class);
        CoachQaService withoutClient = new CoachQaService(
                mock(RecommendationContextRepository.class), emptyProvider, mock(CoachFacts.class),
                mock(com.fitsync.recommendation.service.TodayIntakeSource.class), new ObjectMapper(), new RecommendationProperties(null, null), Clock.systemUTC());

        assertThatThrownBy(() -> withoutClient.ask(userId, "Anyone there?"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
