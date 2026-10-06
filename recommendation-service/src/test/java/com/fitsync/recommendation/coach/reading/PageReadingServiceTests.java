package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.client.UserProfileClient;
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
 * The nutrition and progress pages through the whole service: the same stored reading, background
 * generation and validation as the workout page, with the facts of their own upstreams.
 *
 * <p>{@code coachReadingExecutor} is a same-thread executor here, exactly as in
 * {@link CoachReadingServiceGenerationTests}, so a generation the first call enqueues has already run
 * when the second call reads the row back. Each test uses a fresh random user, because the store
 * writes in its own transactions and nothing here can be rolled back.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class PageReadingServiceTests {

    private static final String TOKEN = "a-very-secret-bearer-value";

    private static final String NUTRITION_MODEL_ANSWER = "{\"headline\": \"Your protein came mostly at lunch and dinner.\", "
            + "\"body\": \"You ate 163.8 g of protein a day [1], up from 162.2 g, and 109.9 g [3] of it came at lunch and dinner.\"}";

    private static final String PROGRESS_MODEL_ANSWER = "{\"headline\": \"Your weight is falling slowly.\", "
            + "\"body\": \"You weighed 71.1 kg on 28 Sept [1], which is down 0.21 kg a week [2].\"}";

    @Autowired
    private CoachReadingService service;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @MockBean
    private NutritionCatalogueClient nutritionClient;

    @MockBean
    private UserProfileClient userClient;

    @MockBean
    private ClaudeClient claudeClient;

    private UUID userId;

    @BeforeEach
    void freshUser() {
        userId = UUID.randomUUID();
    }

    // --- nutrition ------------------------------------------------------------------------------

    @Test
    @DisplayName("nutrition: the first call is READY with rules and starts a generation, the second serves the model's reading")
    void nutritionRulesThenModel() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(NUTRITION_MODEL_ANSWER);

        CoachReadingResponse first = service.nutritionReading(userId, TOKEN, "UTC");

        assertThat(first.surface()).isEqualTo(ReadingSurface.NUTRITION);
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.period()).isEqualTo("PREVIOUS_WEEK");
        assertThat(first.periodStart()).isEqualTo(LocalDate.parse("2026-09-21"));
        assertThat(first.facts()).extracting(ReadingFact::key).containsExactly(
                "PROTEIN_PER_DAY", "PROTEIN_BREAKFAST", "PROTEIN_LUNCH_DINNER", "CALORIES_PER_DAY");
        assertThat(first.lifts()).isEmpty();
        assertThat(first.reading().source()).isEqualTo("RULES");
        assertThat(first.reading().headline()).isEqualTo("Most of your protein came at lunch and dinner.");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).complete(system.capture(), anyString());
        assertThat(system.getValue()).isEqualTo(NutritionReadingPage.SYSTEM_PROMPT);

        CoachReadingResponse second = service.nutritionReading(userId, TOKEN, "UTC");

        assertThat(second.readingId()).isEqualTo(first.readingId());
        assertThat(second.reading().source()).isEqualTo("MODEL");
        assertThat(second.reading().headline()).isEqualTo("Your protein came mostly at lunch and dinner.");
        assertThat(second.reading().citations()).containsExactly(1, 3);
        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.NUTRITION)).isPresent();
        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.WORKOUT)).isEmpty();        // The nutrition page reads the current week so far.
        verify(nutritionClient, atLeastOnce()).pageInsight(TOKEN, "UTC", true);
        verify(nutritionClient, never()).pageInsight(TOKEN, "UTC", false);
    }

    @Test
    @DisplayName("nutrition: a period of NONE is EMPTY, with no reading id and no model call")
    void nutritionNoneIsEmpty() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.noNutrition());

        CoachReadingResponse response = service.nutritionReading(userId, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.EMPTY);
        assertThat(response.readingId()).isNull();
        assertThat(response.reading()).isNull();
        assertThat(response.period()).isEqualTo("NONE");
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("nutrition: an upstream that cannot be read is unavailable")
    void nutritionUpstreamFailure() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> service.nutritionReading(userId, TOKEN, "UTC"))
                .isInstanceOf(ReadingUnavailableException.class)
                .hasMessageContaining("nutrition");
    }

    @Test
    @DisplayName("nutrition: a changed week has a different reading id")
    void nutritionChangedWeekChangesTheId() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition());
        String before = service.nutritionReading(userId, TOKEN, "UTC").readingId();

        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition(
                new java.math.BigDecimal("170"), null, null, null, List.of()));
        String after = service.nutritionReading(userId, TOKEN, "UTC").readingId();

        assertThat(after).isNotEqualTo(before);
    }

    // --- progress -------------------------------------------------------------------------------

    @Test
    @DisplayName("progress: the first call is READY with rules over four facts, the second serves the model's reading")
    void progressRulesThenModel() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.progress());
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(PROGRESS_MODEL_ANSWER);

        CoachReadingResponse first = service.progressReading(userId, TOKEN, "UTC");

        assertThat(first.surface()).isEqualTo(ReadingSurface.PROGRESS);
        assertThat(first.status()).isEqualTo(ReadingStatus.READY);
        assertThat(first.period()).isEqualTo("LAST_28_DAYS");
        assertThat(first.periodStart()).isEqualTo(LocalDate.parse("2026-09-04"));
        assertThat(first.periodEnd()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(first.facts()).extracting(ReadingFact::key).containsExactly(
                "WEIGHT", "WEEKLY_RATE", "GOAL", "CALORIES_PER_DAY");
        assertThat(first.reading().source()).isEqualTo("RULES");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).complete(system.capture(), anyString());
        assertThat(system.getValue()).isEqualTo(ProgressReadingPage.SYSTEM_PROMPT);

        CoachReadingResponse second = service.progressReading(userId, TOKEN, "UTC");

        assertThat(second.readingId()).isEqualTo(first.readingId());
        assertThat(second.reading().source()).isEqualTo("MODEL");
        assertThat(second.reading().citations()).containsExactly(1, 2);        // The progress page keeps its calorie fact on the last full week.
        verify(nutritionClient, atLeastOnce()).pageInsight(TOKEN, "UTC", false);
        verify(nutritionClient, never()).pageInsight(TOKEN, "UTC", true);
    }

    @Test
    @DisplayName("progress: when the nutrition call returns nothing the reading is built without calories")
    void progressWithoutCalories() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.progress());
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(null);

        CoachReadingResponse response = service.progressReading(userId, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.READY);
        assertThat(response.facts()).extracting(ReadingFact::key).containsExactly("WEIGHT", "WEEKLY_RATE", "GOAL");
    }

    @Test
    @DisplayName("progress: when the nutrition call throws the reading still succeeds without calories")
    void progressSurvivesTheSecondCallFailing() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.progress());
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("down"));

        CoachReadingResponse response = service.progressReading(userId, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.READY);
        assertThat(response.facts()).hasSize(3);
    }

    @Test
    @DisplayName("progress: a nutrition period of NONE leaves calories out")
    void progressWithNoNutritionWeek() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.progress());
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.noNutrition());

        assertThat(service.progressReading(userId, TOKEN, "UTC").facts()).hasSize(3);
    }

    @Test
    @DisplayName("progress: no weight at all is EMPTY, and the nutrition upstream is not needed")
    void progressWithoutWeightIsEmpty() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.noWeight());

        CoachReadingResponse response = service.progressReading(userId, TOKEN, "UTC");

        assertThat(response.status()).isEqualTo(ReadingStatus.EMPTY);
        assertThat(response.readingId()).isNull();
        assertThat(response.facts()).isEmpty();
        assertThat(response.period()).isEqualTo("LAST_28_DAYS");
        verify(nutritionClient, never()).pageInsight(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("progress: an upstream that cannot be read is unavailable")
    void progressUpstreamFailure() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.progressReading(userId, TOKEN, "UTC"))
                .isInstanceOf(ReadingUnavailableException.class)
                .hasMessageContaining("user service");
    }

    // --- pinned readings for a chat turn ----------------------------------------------------------

    @Test
    @DisplayName("a pinned nutrition reading resolves against its own id and carries the rule-written text")
    void pinnedNutritionReading() {
        NutritionInsight insight = PageInsightFixtures.nutrition();
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(insight);
        String readingId = service.nutritionReading(userId, TOKEN, "UTC").readingId();

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", readingId);

        assertThat(pinned).isPresent();
        assertThat(pinned.get().readingId()).isEqualTo(readingId);
        assertThat(pinned.get().facts()).hasSize(4);
        assertThat(pinned.get().source()).isEqualTo("RULES");
        assertThat(pinned.get().describesAWeek()).isTrue();
        assertThat(service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", "0000000000000000"))
                .isEmpty();
        assertThat(service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", null)).isEmpty();
    }

    @Test
    @DisplayName("a pinned progress reading is not a week, and an id from another page does not match it")
    void pinnedProgressReading() {
        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.progress());
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition());
        String progressId = service.progressReading(userId, TOKEN, "UTC").readingId();
        String nutritionId = service.nutritionReading(userId, TOKEN, "UTC").readingId();

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedReading(ReadingSurface.PROGRESS, userId, TOKEN, "UTC", progressId);

        assertThat(pinned).isPresent();
        assertThat(pinned.get().period()).isEqualTo("LAST_28_DAYS");
        assertThat(pinned.get().describesAWeek()).isFalse();
        assertThat(pinned.get().describesCurrentWeek()).isFalse();
        assertThat(service.pinnedReading(ReadingSurface.PROGRESS, userId, TOKEN, "UTC", nutritionId)).isEmpty();
    }

    @Test
    @DisplayName("a pinned reading is empty for an unreadable upstream, an empty period, and never writes a row")
    void pinnedReadingDegrades() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(null);
        assertThat(service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", "0123456789abcdef")).isEmpty();

        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("down"));
        assertThat(service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", "0123456789abcdef")).isEmpty();

        when(userClient.progressInsight(anyString(), anyString())).thenReturn(PageInsightFixtures.noWeight());
        assertThat(service.pinnedReading(ReadingSurface.PROGRESS, userId, TOKEN, "UTC", "0123456789abcdef")).isEmpty();

        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.NUTRITION)).isEmpty();
        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.PROGRESS)).isEmpty();
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("a pinned reading prefers a stored model reading for the same facts")
    void pinnedPrefersTheStoredModelReading() {
        when(nutritionClient.pageInsight(anyString(), anyString(), anyBoolean())).thenReturn(PageInsightFixtures.nutrition());
        when(claudeClient.complete(anyString(), anyString())).thenReturn(NUTRITION_MODEL_ANSWER);
        String readingId = service.nutritionReading(userId, TOKEN, "UTC").readingId();
        service.nutritionReading(userId, TOKEN, "UTC");

        Optional<CoachReadingService.PinnedReading> pinned =
                service.pinnedReading(ReadingSurface.NUTRITION, userId, TOKEN, "UTC", readingId);

        assertThat(pinned).isPresent();
        assertThat(pinned.get().source()).isEqualTo("MODEL");
        assertThat(pinned.get().headline()).isEqualTo("Your protein came mostly at lunch and dinner.");
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
