package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Weekly reviews: the two-fact minimum, the current week written "so far" and written again when its
 * facts change, a week that ended served from its row, each user's weeks kept to that user, and a
 * model review replacing the rule review once it passes the check.
 *
 * <p>Today is Thursday 1 Oct 2026 in UTC, so the current week runs from 28 Sept and the week before it
 * from 21 Sept. Generations run on the calling thread, so a model review the first call starts is
 * already stored when the second call reads.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class WeeklyReviewServiceTests {

    private static final String CALLER = "caller-a";
    private static final String OTHER_CALLER = "caller-b";
    private static final LocalDate THIS_MONDAY = LocalDate.parse("2026-09-28");
    private static final LocalDate THIS_SUNDAY = LocalDate.parse("2026-10-04");
    private static final LocalDate LAST_MONDAY = LocalDate.parse("2026-09-21");
    private static final LocalDate LAST_SUNDAY = LocalDate.parse("2026-09-27");

    @Autowired
    private WeeklyReviewService service;

    @Autowired
    private CoachPageReadingRepository repository;

    @MockBean
    private WorkoutCatalogueClient workoutClient;

    @MockBean
    private AnalyticsProgressClient analyticsClient;

    @MockBean
    private ClaudeClient claudeClient;

    private UUID userId;

    @BeforeEach
    void freshUser() {
        userId = UUID.randomUUID();
        reset(workoutClient, analyticsClient, claudeClient);
        // Every week answers with nothing recorded unless a test says otherwise.
        when(workoutClient.trainingProgress(anyString(), any(), any(), any())).thenReturn("{}");
        when(analyticsClient.goalProgress(anyString(), any(), any(), any())).thenReturn("{}");
        when(analyticsClient.foodProgress(anyString(), any(), any(), any())).thenReturn("{}");
        // The model's answer is not valid JSON, so model reviews are rejected unless a test says otherwise.
        when(claudeClient.complete(anyString(), anyString())).thenReturn("not json");
    }

    @Test
    @DisplayName("a week with two facts is READY and a week with one is NONE, newest first")
    void twoFactMinimum() {
        trainingFor(CALLER, THIS_MONDAY, THIS_SUNDAY, training(3, 2, 120, 90, 0));
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, "{\"hero\":{\"sessions\":{\"value\":1}}}");

        List<WeeklyReviewView> reviews = service.reviews(userId, CALLER, "UTC", 2);

        assertThat(reviews).extracting(WeeklyReviewView::weekStart).containsExactly(THIS_MONDAY, LAST_MONDAY);
        WeeklyReviewView current = reviews.get(0);
        assertThat(current.current()).isTrue();
        assertThat(current.weekEnd()).isEqualTo(THIS_SUNDAY);
        assertThat(current.status()).isEqualTo(WeeklyReviewStatus.READY);
        assertThat(current.source()).isEqualTo("RULES");
        assertThat(current.headline()).isEqualTo("Your week so far.");
        assertThat(current.body()).isEqualTo("You trained 3 sessions over 120 minutes [1] [2].");
        assertThat(current.facts()).extracting(ReadingFact::key, ReadingFact::card).containsExactly(
                org.assertj.core.groups.Tuple.tuple("SESSIONS", "TRAINING"),
                org.assertj.core.groups.Tuple.tuple("MINUTES", "TRAINING"));
        assertThat(current.facts().get(0).note()).isEqualTo("Up 1");

        WeeklyReviewView last = reviews.get(1);
        assertThat(last.status()).isEqualTo(WeeklyReviewStatus.NONE);
        assertThat(last.headline()).isNull();
        assertThat(last.facts()).hasSize(1);
    }

    @Test
    @DisplayName("planned days done joins the facts from the weekly insight of the same week")
    void plannedDaysDoneJoinsTheFacts() {
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, training(3, 2, 120, 90, 0));
        when(workoutClient.weeklyInsight(CALLER, "UTC", LAST_SUNDAY))
                .thenReturn("{\"plannedDays\":4,\"plannedDaysDone\":3}");

        WeeklyReviewView last = service.reviews(userId, CALLER, "UTC", 2).get(1);

        ReadingFact planned = last.facts().stream().filter(fact -> fact.key().equals("PLANNED_DAYS"))
                .findFirst().orElseThrow();
        assertThat(planned.value()).isEqualTo("3");
        assertThat(planned.unit()).isEqualTo("of 4");
        assertThat(planned.detail()).isEqualTo("3 of 4 planned days");
        assertThat(planned.card()).isEqualTo("TRAINING");
    }

    @Test
    @DisplayName("before a day of the current week is over, food reads today so far and is never compared")
    void currentWeekWithoutAFullDayReadsTodaySoFar() {
        when(workoutClient.trainingProgress(CALLER, "UTC", THIS_MONDAY, THIS_SUNDAY)).thenReturn("""
                {"hero":{"sessions":{"value":0,"previous":2},"minutes":{"value":0,"previous":90}},
                 "load":{"currentWeek":{"weekStart":"2026-09-28","soFar":180,"sessions":1,"minutes":30}}}""");
        when(analyticsClient.foodProgress(CALLER, "UTC", THIS_MONDAY, THIS_SUNDAY)).thenReturn("""
                {"hero":{"eatenPerDay":{"value":null,"previous":2032},"proteinPerDay":{"value":null,"previous":157},
                 "daysLogged":{"value":0,"previous":7,"days":7}},
                 "currentWeek":{"included":false,"daysLogged":0,"eatenTodaySoFarKcal":476,"proteinTodaySoFarGrams":29.0}}""");

        List<WeeklyReviewView> reviews = service.reviews(userId, CALLER, "UTC", 2);
        WeeklyReviewView current = reviews.get(0);

        assertThat(current.facts()).extracting(ReadingFact::key).containsExactly("SESSIONS", "MINUTES",
                "CALORIES_PER_DAY", "PROTEIN_PER_DAY");
        // Sessions and minutes count today, from the week in progress.
        assertThat(current.facts().get(0).value()).isEqualTo("1");
        assertThat(current.facts().get(1).value()).isEqualTo("30");
        ReadingFact calories = current.facts().get(2);
        assertThat(calories.label()).isEqualTo("Calories today");
        assertThat(calories.value()).isEqualTo("476");
        assertThat(calories.detail()).isEqualTo("today so far");
        assertThat(calories.note()).isEmpty();
        assertThat(current.facts().get(3).value()).isEqualTo("29");
        assertThat(current.facts().get(3).detail()).isEqualTo("today so far");
        assertThat(current.body()).contains("So far today you ate 476 kcal and 29 g of protein [3] [4].");
        assertThat(current.eatenTodaySoFarKcal()).isEqualTo(476);
        assertThat(current.proteinTodaySoFarGrams()).isEqualByComparingTo("29.0");

        WeeklyReviewView last = reviews.get(1);
        assertThat(last.eatenTodaySoFarKcal()).isNull();
        assertThat(last.proteinTodaySoFarGrams()).isNull();
    }

    @Test
    @DisplayName("with full days the current week averages them only, and still carries today so far")
    void currentWeekWithFullDaysAveragesThemOnly() {
        trainingFor(CALLER, THIS_MONDAY, THIS_SUNDAY, training(2, 2, 90, 90, 0));
        when(analyticsClient.foodProgress(CALLER, "UTC", THIS_MONDAY, THIS_SUNDAY)).thenReturn("""
                {"hero":{"eatenPerDay":{"value":1238,"previous":2032},"proteinPerDay":{"value":89.5,"previous":157},
                 "daysLogged":{"value":2,"previous":7,"days":7}},
                 "currentWeek":{"included":true,"daysLogged":2,"eatenTodaySoFarKcal":300,"proteinTodaySoFarGrams":20.0}}""");

        WeeklyReviewView current = service.reviews(userId, CALLER, "UTC", 1).get(0);

        ReadingFact calories = current.facts().stream().filter(fact -> fact.key().equals("CALORIES_PER_DAY"))
                .findFirst().orElseThrow();
        assertThat(calories.label()).isEqualTo("Calories a day");
        assertThat(calories.value()).isEqualTo("1,238");
        assertThat(calories.detail()).isEqualTo("on 2 logged days");
        assertThat(current.eatenTodaySoFarKcal()).isEqualTo(300);
        assertThat(current.proteinTodaySoFarGrams()).isEqualByComparingTo("20.0");
    }

    @Test
    @DisplayName("all three pages give a week's facts, each naming its card, and the rule review cites them")
    void factsFromAllThreePages() {
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, training(4, 3, 190, 150, 1));
        when(analyticsClient.foodProgress(CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY)).thenReturn("""
                {"hero":{"eatenPerDay":{"value":2105,"previous":2230},"proteinPerDay":{"value":128,"previous":120},
                 "balancePerDay":{"value":-320,"daysLogged":6},"daysLogged":{"value":6,"previous":7,"days":7}}}""");
        when(analyticsClient.goalProgress(CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY)).thenReturn("""
                {"hero":{"changeKg":-0.4},"weight":{"weekly":[{"weekStart":"2026-09-21","avgKg":80.6,"weighIns":3}]}}""");

        WeeklyReviewView last = service.reviews(userId, CALLER, "UTC", 2).get(1);

        assertThat(last.status()).isEqualTo(WeeklyReviewStatus.READY);
        assertThat(last.current()).isFalse();
        assertThat(last.headline()).isEqualTo("A week with a new record.");
        assertThat(last.facts()).extracting(ReadingFact::key).containsExactly("SESSIONS", "MINUTES", "RECORDS",
                "CALORIES_PER_DAY", "PROTEIN_PER_DAY", "BALANCE_PER_DAY", "WEIGHT_CHANGE");
        assertThat(last.facts()).extracting(ReadingFact::card).containsExactly("TRAINING", "TRAINING", "TRAINING",
                "FOOD", "FOOD", "FOOD", "GOAL");
        assertThat(last.body()).startsWith("You trained 4 sessions over 190 minutes [1] [2]. You set 1 record [3].");
        assertThat(last.citations()).isNotEmpty();
    }

    @Test
    @DisplayName("the current week is written so far, and written again when its facts change")
    void currentWeekIsRewrittenWhenItsFactsChange() {
        trainingFor(CALLER, THIS_MONDAY, THIS_SUNDAY, training(3, 2, 120, 90, 0));
        WeeklyReviewView first = service.reviews(userId, CALLER, "UTC", 1).get(0);
        String firstHash = row(THIS_MONDAY).getFactsHash();

        trainingFor(CALLER, THIS_MONDAY, THIS_SUNDAY, training(4, 2, 165, 90, 0));
        WeeklyReviewView second = service.reviews(userId, CALLER, "UTC", 1).get(0);

        assertThat(first.body()).contains("3 sessions over 120 minutes");
        assertThat(second.headline()).isEqualTo("Your week so far.");
        assertThat(second.body()).contains("4 sessions over 165 minutes");
        assertThat(row(THIS_MONDAY).getFactsHash()).isNotEqualTo(firstHash);
        verify(workoutClient, times(2)).trainingProgress(CALLER, "UTC", THIS_MONDAY, THIS_SUNDAY);
    }

    @Test
    @DisplayName("a week that ended is read once and served from its row after that")
    void anEndedWeekIsServedFromItsRow() {
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, training(4, 3, 190, 150, 0));

        WeeklyReviewView first = service.reviews(userId, CALLER, "UTC", 2).get(1);
        WeeklyReviewView second = service.reviews(userId, CALLER, "UTC", 2).get(1);

        verify(workoutClient, times(1)).trainingProgress(CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY);
        verify(analyticsClient, times(1)).foodProgress(CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY);
        assertThat(second.facts()).isEqualTo(first.facts());
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(second.status()).isEqualTo(WeeklyReviewStatus.READY);
    }

    @Test
    @DisplayName("each user's weeks are read with their own credential and stored under their own id")
    void ownersAreIsolated() {
        UUID otherUser = UUID.randomUUID();
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, training(3, 2, 120, 90, 0));
        trainingFor(OTHER_CALLER, LAST_MONDAY, LAST_SUNDAY, training(5, 4, 250, 200, 0));

        WeeklyReviewView mine = service.reviews(userId, CALLER, "UTC", 2).get(1);
        WeeklyReviewView theirs = service.reviews(otherUser, OTHER_CALLER, "UTC", 2).get(1);

        assertThat(mine.body()).contains("3 sessions");
        assertThat(theirs.body()).contains("5 sessions");
        verify(workoutClient).trainingProgress(OTHER_CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY);
        assertThat(repository.findByUserIdAndSurfaceAndPeriodKey(userId, ReadingSurface.PROGRESS_WEEK,
                LAST_MONDAY.toString()).orElseThrow().getFactsJson()).contains("\"value\":\"3\"");
        assertThat(repository.findByUserIdAndSurfaceAndPeriodKey(otherUser, ReadingSurface.PROGRESS_WEEK,
                LAST_MONDAY.toString()).orElseThrow().getFactsJson()).contains("\"value\":\"5\"");
    }

    @Test
    @DisplayName("a model review that passes the check replaces the rule review on the next call")
    void modelReviewReplacesTheRuleReview() {
        trainingFor(CALLER, LAST_MONDAY, LAST_SUNDAY, training(3, 3, 120, 120, 0));
        when(claudeClient.complete(anyString(), anyString())).thenReturn(
                "{\"headline\":\"Your week of training.\",\"body\":\"You trained 3 sessions [1] over 120 minutes [2].\"}");

        WeeklyReviewView first = service.reviews(userId, CALLER, "UTC", 2).get(1);
        WeeklyReviewView second = service.reviews(userId, CALLER, "UTC", 2).get(1);

        assertThat(first.source()).isEqualTo("RULES");
        assertThat(second.source()).isEqualTo("MODEL");
        assertThat(second.headline()).isEqualTo("Your week of training.");
        assertThat(second.citations()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("a page that cannot be read leaves the week shown from the rest, unstored, and is asked once a request")
    void anUnreadablePageIsNotStored() {
        when(workoutClient.trainingProgress(anyString(), any(), any(), any())).thenReturn(null);
        when(analyticsClient.foodProgress(CALLER, "UTC", LAST_MONDAY, LAST_SUNDAY)).thenReturn("""
                {"hero":{"eatenPerDay":{"value":2105},"proteinPerDay":{"value":128},"daysLogged":{"value":6}}}""");

        List<WeeklyReviewView> reviews = service.reviews(userId, CALLER, "UTC", 3);

        assertThat(reviews.get(1).status()).isEqualTo(WeeklyReviewStatus.READY);
        assertThat(reviews.get(1).facts()).extracting(ReadingFact::key)
                .containsExactly("CALORIES_PER_DAY", "PROTEIN_PER_DAY");
        assertThat(repository.findByUserIdAndSurfaceAndPeriodKey(userId, ReadingSurface.PROGRESS_WEEK,
                LAST_MONDAY.toString())).isEmpty();
        verify(workoutClient, times(1)).trainingProgress(anyString(), any(), any(), any());
        verify(claudeClient, never()).complete(anyString(), anyString());
    }

    private void trainingFor(String caller, LocalDate monday, LocalDate sunday, String body) {
        when(workoutClient.trainingProgress(eq(caller), eq("UTC"), eq(monday), eq(sunday))).thenReturn(body);
    }

    private CoachPageReadingEntity row(LocalDate monday) {
        return repository.findByUserIdAndSurfaceAndPeriodKey(userId, ReadingSurface.PROGRESS_WEEK, monday.toString())
                .orElseThrow();
    }

    private static String training(int sessions, int previousSessions, int minutes, int previousMinutes, int records) {
        return """
                {"hero":{"sessions":{"value":%d,"previous":%d},"minutes":{"value":%d,"previous":%d},
                 "records":{"value":%d,"exercises":["Barbell Squat"]}}}""".formatted(
                sessions, previousSessions, minutes, previousMinutes, records);
    }

    @TestConfiguration
    static class FixedClockAndSyncExecutor {

        @Bean(name = "clock")
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean(name = "coachReadingExecutor")
        ExecutorService coachReadingExecutor() {
            return new AbstractExecutorService() {
                private volatile boolean shutdown;

                @Override
                public void execute(Runnable command) {
                    command.run();
                }

                @Override
                public void shutdown() {
                    shutdown = true;
                }

                @Override
                public List<Runnable> shutdownNow() {
                    shutdown = true;
                    return List.of();
                }

                @Override
                public boolean isShutdown() {
                    return shutdown;
                }

                @Override
                public boolean isTerminated() {
                    return shutdown;
                }

                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) {
                    return true;
                }
            };
        }
    }
}
