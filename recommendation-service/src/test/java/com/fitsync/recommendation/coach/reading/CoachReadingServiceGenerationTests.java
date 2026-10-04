package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
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
 * <p>{@link RuleReadingWriter} is mocked here too, but only so one test can force it empty; every
 * other test lets the mock delegate to a real instance, because the facts used throughout this class
 * are the same full, demo-shaped facts the rule writer always has something to say about. That is why
 * most of these scenarios are READY with source RULES on the very first call: the fixed-rule reading
 * is available immediately, and only the *source* of a later READY response — RULES the first time,
 * MODEL once a generation this same call enqueued has been accepted — changes from call to call.
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

    private static final String HEADLINE = "Your best single rose while your sessions stayed the same length.";
    private static final String BODY =
            "Your dumbbell bench press best single rose 12% to 30 kg [1], and each session still took 50 min [2].";

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

    @MockBean
    private RuleReadingWriter ruleReadingWriter;

    private final RuleReadingWriter realRuleReadingWriter =
            new RuleReadingWriter(new ReadingValidator(new CoachGuardrail()));

    private UUID userId;

    @BeforeEach
    void freshUserAndStubbedClock() {
        userId = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        // The default stub is the real writer, so every test not exercising the "nothing to say"
        // fallback sees the same rule-written reading a production deployment would. doAnswer, not
        // when(...), because when(mock.write(any(), any())) would call the mock with the any()
        // matchers' null placeholders through whatever is already stubbed - including, on the second
        // such call in a test that re-stubs this, the real delegate below itself.
        doAnswer(invocation -> realRuleReadingWriter.write(invocation.getArgument(0), invocation.getArgument(1)))
                .when(ruleReadingWriter).write(any(), any());
    }

    @Test
    @DisplayName("the first call is READY with a rule-written reading and enqueues a generation; "
            + "the second call is READY with the model's reading")
    void firstCallReadyWithRulesThenSecondCallReadyWithModel() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.reading().source()).isEqualTo("RULES");

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.READY);
        assertThat(second.readingId()).isEqualTo(first.readingId());
        assertThat(second.reading().headline()).isEqualTo(HEADLINE);
        assertThat(second.reading().citations()).containsExactly(1, 2);
        assertThat(second.reading().source()).isEqualTo("MODEL");
    }

    @Test
    @DisplayName("a rejected first answer is repaired, the repair prompt lists the violations, and a later call is READY with MODEL")
    void repairedAfterAFailedFirstAttempt() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString()))
                .thenReturn("not valid json at all")
                .thenReturn(validAnswerJson());

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.reading().source()).isEqualTo("RULES");

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(2)).complete(anyString(), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("was not accepted").contains("It was not a single JSON object.");

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.READY);
        assertThat(second.reading().source()).isEqualTo("MODEL");
    }

    @Test
    @DisplayName("two rejected answers stay READY with RULES, and a later call within the hour does not call the model again")
    void twoRejectedAnswersDoNotCallTheModelAgainWithinTheHour() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn("not valid json at all");

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.reading().source()).isEqualTo("RULES");
        verify(claudeClient, times(2)).complete(anyString(), anyString());

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.READY);
        assertThat(second.reading().source()).isEqualTo("RULES");
        verify(claudeClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("an unreachable model still serves RULES, is not retried within two minutes, and is asked again after")
    void anUnreachableModelIsAskedAgainAfterTwoMinutes() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection refused"))
                .thenReturn(validAnswerJson());

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.reading().source()).isEqualTo("RULES");
        // A model that could not be reached is not asked a second time in the same attempt.
        verify(claudeClient, times(1)).complete(anyString(), anyString());

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.READY);
        assertThat(second.reading().source()).isEqualTo("RULES");
        verify(claudeClient, times(1)).complete(anyString(), anyString());

        when(clock.instant()).thenReturn(NOW.plusSeconds(121));
        CoachReadingResponse third = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(third.status()).isEqualTo(ReadingStatus.READY);
        verify(claudeClient, times(2)).complete(anyString(), anyString());

        CoachReadingResponse fourth = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(fourth.reading().source()).isEqualTo("MODEL");
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
                .contains("Estimated best single, Dumbbell Bench Press")
                .doesNotContain("Weight moved");
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

    // --- pinnedWorkoutReading, the read-only path a coach turn uses to cite a reading -------------

    @Test
    @DisplayName("a matching reading id returns the facts and the rule-written text, with no side effect")
    void pinnedReadingWithMatchingIdReturnsFactsAndRuleWrittenText() {
        TrainingInsight insight = fullInsight();
        List<ReadingFact> facts = factsBuilder.build(insight);
        String readingId = CoachReadingService.hash(insight, facts).substring(0, 16);
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insight);

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", readingId);

        assertThat(pinned).isPresent();
        assertThat(pinned.get().readingId()).isEqualTo(readingId);
        assertThat(pinned.get().source()).isEqualTo("RULES");
        assertThat(pinned.get().headline()).isNotBlank();
        assertThat(pinned.get().body()).isNotBlank();
        assertThat(pinned.get().facts()).hasSize(4);
        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.WORKOUT)).isEmpty();
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("a stored ACCEPTED model reading is preferred over the rule-written one")
    void pinnedReadingPrefersAStoredAcceptedModelReading() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        // Same sequence as firstCallReadyWithRulesThenSecondCallReadyWithModel: the first call
        // enqueues a generation the synchronous test executor already ran, so the second call reads
        // it back as ACCEPTED.
        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        service.workoutReading(userId, TOKEN, "UTC");
        verify(claudeClient, times(1)).complete(anyString(), anyString());

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", first.readingId());

        assertThat(pinned).isPresent();
        assertThat(pinned.get().source()).isEqualTo("MODEL");
        assertThat(pinned.get().headline()).isEqualTo(HEADLINE);
        assertThat(pinned.get().body()).isEqualTo(BODY);
        // Resolving a pinned reading must not itself call the model again.
        verify(claudeClient, times(1)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("a different reading id returns empty")
    void pinnedReadingWithADifferentIdReturnsEmpty() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", "0000000000000000");

        assertThat(pinned).isEmpty();
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("an unavailable upstream returns empty and never throws")
    void pinnedReadingWithUpstreamUnavailableReturnsEmpty() {
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(null);

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", "0000000000000000");

        assertThat(pinned).isEmpty();
    }

    @Test
    @DisplayName("an upstream that throws still returns empty rather than propagating")
    void pinnedReadingWithUpstreamThrowingReturnsEmpty() {
        when(workoutClient.trainingInsight(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection refused"));

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", "0000000000000000");

        assertThat(pinned).isEmpty();
    }

    @Test
    @DisplayName("period NONE returns empty")
    void pinnedReadingWithPeriodNoneReturnsEmpty() {
        TrainingInsight insight = new TrainingInsight("UTC", LocalDate.parse("2026-09-28"), "NONE", null, null,
                0, 0, null, null, null, null, null, null, List.of());
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insight);

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedWorkoutReading(userId, TOKEN, "UTC", "0000000000000000");

        assertThat(pinned).isEmpty();
    }

    @Test
    @DisplayName("resolving a pinned reading never claims a pending row or enqueues a generation")
    void pinnedReadingHasNoSideEffect() {
        TrainingInsight insight = fullInsight();
        List<ReadingFact> facts = factsBuilder.build(insight);
        String readingId = CoachReadingService.hash(insight, facts).substring(0, 16);
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(insight);
        when(claudeClient.complete(anyString(), anyString())).thenReturn(validAnswerJson());

        service.pinnedWorkoutReading(userId, TOKEN, "UTC", readingId);

        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.WORKOUT))
                .withFailMessage("pinnedWorkoutReading must never write a row")
                .isEmpty();
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("when the rule writer has nothing to say, the response falls back to WRITING then RESTING")
    void ruleWriterEmptyFallsBackToWritingThenResting() {
        doReturn(Optional.empty()).when(ruleReadingWriter).write(any(), any());
        when(workoutClient.trainingInsight(anyString(), anyString())).thenReturn(fullInsight());
        when(claudeClient.complete(anyString(), anyString())).thenReturn("not valid json at all");

        CoachReadingResponse first = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(first.status()).isEqualTo(ReadingStatus.WRITING);
        assertThat(first.reading()).isNull();

        CoachReadingResponse second = service.workoutReading(userId, TOKEN, "UTC");
        assertThat(second.status()).isEqualTo(ReadingStatus.RESTING);
        assertThat(second.reading()).isNull();
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
                        new BigDecimal("22.50"), LocalDate.parse("2026-09-25"))))
                .withBestSingles(List.of(ReadingFactsBuilderTests.BENCH));
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
                List.of()).withBestSingles(List.of(ReadingFactsBuilderTests.BENCH));
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
