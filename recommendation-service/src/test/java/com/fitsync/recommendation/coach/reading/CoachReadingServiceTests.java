package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * The two paths that need no model at all: an empty period, and no AI tier configured. Neither
 * declares a {@code ClaudeClient} bean, so {@code ObjectProvider.getIfAvailable()} genuinely returns
 * null, exactly as it would with the AI tier switched off in a real deployment.
 *
 * <p>With no model configured at all there is still a reading: the rule writer runs for every real
 * period with at least two facts, and its output is what {@code READY} serves as {@code RULES}.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoachReadingServiceTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN = "the-token";

    @Autowired
    private CoachReadingService service;

    @MockBean
    private WorkoutCatalogueClient workoutClient;

    @Test
    @DisplayName("upstream unavailable surfaces as a 503, not a 500")
    void upstreamUnavailableThrows() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.workoutReading(USER_ID, TOKEN, "UTC"))
                .isInstanceOf(ReadingUnavailableException.class);
    }

    @Test
    @DisplayName("period NONE is EMPTY, with no reading and no reading id")
    void periodNoneIsEmpty() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insightWithPeriod("NONE"));

        CoachReadingResponse response = service.workoutReading(USER_ID, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.EMPTY);
        assertThat(response.readingId()).isNull();
        assertThat(response.reading()).isNull();
    }

    @Test
    @DisplayName("fewer than two facts is EMPTY even when the period is real")
    void tooFewFactsIsEmpty() {
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                1, 0, null, null, null, null, null, null, List.of());
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insight);

        CoachReadingResponse response = service.workoutReading(USER_ID, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.EMPTY);
    }

    @Test
    @DisplayName("with no ClaudeClient bean at all, a real period is READY with a rule-written reading")
    void noModelMeansReadyWithRules() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());

        CoachReadingResponse response = service.workoutReading(USER_ID, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.READY);
        assertThat(response.readingId()).isNotNull();
        assertThat(response.reading()).isNotNull();
        assertThat(response.reading().source()).isEqualTo("RULES");
        assertThat(response.reading().headline()).isNotBlank();
        assertThat(response.reading().body()).isNotBlank();
        assertThat(response.facts()).hasSize(4);
    }

    private static TrainingInsight insightWithPeriod(String period) {
        return new TrainingInsight("UTC", LocalDate.parse("2026-09-28"), period, null, null,
                0, 0, null, null, null, null, null, null, List.of());
    }

    static TrainingInsight fullInsight() {
        return new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new java.math.BigDecimal("1923.00"), new java.math.BigDecimal("1716.00"),
                50, 50,
                new TrainingInsight.RecordInfo("Dumbbell Bench Press", new java.math.BigDecimal("22.50"),
                        LocalDate.parse("2026-09-21"), new java.math.BigDecimal("20.00")),
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of("Plank Hold")),
                List.of(new TrainingInsight.LiftProgress("Dumbbell Bench Press",
                        new java.math.BigDecimal("20.00"), LocalDate.parse("2026-09-16"),
                        new java.math.BigDecimal("22.50"), LocalDate.parse("2026-09-25"))));
    }
}
