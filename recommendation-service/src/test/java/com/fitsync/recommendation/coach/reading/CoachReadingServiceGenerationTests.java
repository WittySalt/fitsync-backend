package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Everything that happens once an AI tier is actually configured: the model is asked, a failed
 * validation gets one repair attempt, a rejection is remembered so it is not retried within the
 * hour, and a changed page starts over.
 *
 * <p>{@code coachReadingExecutor} is replaced here with a same-thread executor, so a generation
 * this test enqueues has already run by the time {@code workoutReading} returns to the *next*
 * caller — which is also why every scenario here is read as "first call, then a second call to see
 * what the first call's generation produced" rather than a wait or a poll loop.
 *
 * <p>Not {@code @Transactional}: {@link CoachPageReadingStore} deliberately writes in its own,
 * separate transaction ({@code REQUIRES_NEW}) so a concurrent request's write can never be rolled
 * back by this one, and that same property means a test-managed transaction here could not roll
 * those writes back either. Each test uses its own fresh, random user id instead, which isolates it
 * from every other test regardless of what actually got committed.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class CoachReadingServiceGenerationTests {

    private static final String TOKEN = "a-very-secret-bearer-token";
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private static final String HEADLINE = "Your lifts grew while your sessions stayed the same length.";
    private static final String BODY =
            "You moved 1,923 kg a session [1], up from 1,716 kg, and each one still took 50 min [2].";

    @Autowired
    private CoachReadingService service;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @Autowired
    private ReadingFactsBuilder factsBuilder;

    @MockBean
    private WorkoutCatalogueClient workoutClient;

    @MockBean
    private ClaudeClient claudeClient;

    @MockBean
    private Clock clock;

    private UUID userId;

    @BeforeEach
    void freshUserAndStubbedClock() {
        userId = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    @DisplayName("the first call writes, and the second call sees what the generation produced")
    void firstCallWritesThenSecondCallIsReady() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.WRITING);
        assertThat(first.reading()).isNull();

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.READY);
        assertThat(second.readingId()).isEqualTo(first.readingId());
        assertThat(second.reading().headline()).isEqualTo(HEADLINE);
        assertThat(second.reading().citations()).containsExactly(1, 2);
        assertThat(second.reading().source()).isEqualTo("MODEL");
    }

    @Test
    @DisplayName("a rejected first answer is repaired, the repair prompt lists the violations, and the result is READY")
    void repairedAfterAFailedFirstAttempt() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString()))
                .thenReturn("not valid json at all")
                .thenReturn(validAnswerJson());

        service.workoutReading(userId, TOKEN, "UTC");

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(2)).complete(anyString(), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("failed these checks");

        CoachReadingResponse result = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(result.status()).isEqualTo(ReadingStatus.READY);
    }

    @Test
    @DisplayName("two failed attempts end RESTING, and a later call within the hour does not call the model again")
    void twoFailedAttemptsRestAndStayRested() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn("not valid json at all");

        service.workoutReading(userId, TOKEN, "UTC");
        verify(claudeClient, times(2)).complete(anyString(), anyString());

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.RESTING);
        verify(claudeClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("changed facts produce a new reading id and start a new generation")
    void changedFactsStartANewGeneration() {
        when(workoutClient.trainingInsight(anyString(), anyString()))
                .thenReturn(fullInsight())
                .thenReturn(insightWithADifferentRecordDay());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");

        assertThat(second.readingId()).isNotEqualTo(first.readingId());
        verify(claudeClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("the model prompt carries the facts and the period, never the user id or the bearer token")
    void promptExcludesIdentity() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        service.workoutReading(userId, TOKEN, "UTC");

        ArgumentCaptor<String> systemPrompts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompts = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).complete(systemPrompts.capture(), userPrompts.capture());

        assertThat(systemPrompts.getValue()).doesNotContain(TOKEN).doesNotContain(userId.toString());
        assertThat(userPrompts.getValue())
                .doesNotContain(TOKEN)
                .doesNotContain(userId.toString())
                .contains("PREVIOUS_WEEK")
                .contains("Weight moved per session");
    }

    @Test
    @DisplayName("a PENDING row started sixty-one seconds ago is abandoned and generation starts again")
    void abandonedPendingRowIsReclaimed() {
        TrainingInsight insight = fullInsight();
        List<ReadingFact> facts = factsBuilder.build(insight);
        String hash = CoachReadingService.hash(insight, facts);

        CoachPageReadingEntity stale = new CoachPageReadingEntity();
        stale.setId(UUID.randomUUID());
        stale.setUserId(userId);
        stale.setSurface(ReadingSurface.WORKOUT);
        stale.setFactsHash(hash);
        stale.setOutcome(ReadingOutcome.PENDING);
        stale.setStartedAt(NOW.minusSeconds(61));
        readingRepository.saveAndFlush(stale);

        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insight);
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        service.workoutReading(userId, TOKEN, "UTC");

        verify(claudeClient, times(1)).complete(anyString(), anyString());
        CoachPageReadingEntity reclaimed =
                readingRepository.findByUserIdAndSurface(userId, ReadingSurface.WORKOUT).orElseThrow();
        assertThat(reclaimed.getOutcome()).isEqualTo(ReadingOutcome.ACCEPTED);
    }

    private static String validAnswerJson() {
        return "{\"headline\": \"" + HEADLINE + "\", \"body\": \"" + BODY + "\"}";
    }

    static TrainingInsight fullInsight() {
        return new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new BigDecimal("1923.00"), new BigDecimal("1716.00"),
                50, 50,
                new TrainingInsight.RecordInfo("Dumbbell Bench Press", new BigDecimal("22.50"),
                        LocalDate.parse("2026-09-21"), new BigDecimal("20.00")),
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of("Plank Hold")),
                List.of(new TrainingInsight.LiftProgress("Dumbbell Bench Press",
                        new BigDecimal("20.00"), LocalDate.parse("2026-09-16"),
                        new BigDecimal("22.50"), LocalDate.parse("2026-09-25"))));
    }

    /**
     * Same facts as {@link #fullInsight()} except the record's day, so its RECORD fact's note
     * changes (a different weekday) and the hash changes, while the numbers the stubbed model
     * answer cites (facts 1 and 2) are untouched, so the same answer still validates.
     */
    private static TrainingInsight insightWithADifferentRecordDay() {
        return new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new BigDecimal("1923.00"), new BigDecimal("1716.00"),
                50, 50,
                new TrainingInsight.RecordInfo("Dumbbell Bench Press", new BigDecimal("22.50"),
                        LocalDate.parse("2026-09-22"), new BigDecimal("20.00")),
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of("Plank Hold")),
                List.of());
    }

    @TestConfiguration
    static class SyncExecutorConfig {

        @Bean(name = "coachReadingExecutor")
        ExecutorService coachReadingExecutor() {
            return new AbstractExecutorService() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }

                @Override
                public void shutdown() {
                    // Nothing to release: every task already ran, synchronously, on the caller's thread.
                }

                @Override
                public List<Runnable> shutdownNow() {
                    return List.of();
                }

                @Override
                public boolean isShutdown() {
                    return true;
                }

                @Override
                public boolean isTerminated() {
                    return true;
                }

                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) {
                    return true;
                }
            };
        }
    }
}
