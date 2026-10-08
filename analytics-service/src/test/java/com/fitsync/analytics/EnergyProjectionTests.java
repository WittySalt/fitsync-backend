package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import com.fitsync.analytics.messaging.AccountDeletionProjectionService;
import com.fitsync.analytics.messaging.EnergyProfileComputedEventConsumerImpl;
import com.fitsync.analytics.messaging.EnergyProfileComputedKafkaListener;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.service.AnalyticsRebuildService;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** Energy this week: the events that feed it, and what the rebuild and the account deletion do with them. */
@SpringBootTest
@ActiveProfiles("test")
class EnergyProjectionTests {

    @Autowired
    private DomainEventConsumer<EnergyProfileComputedPayload> energyConsumer;
    @Autowired
    private DomainEventConsumer<MealLoggedPayload> mealLoggedConsumer;
    @Autowired
    private DomainEventConsumer<MealUpdatedPayload> mealUpdatedConsumer;
    @Autowired
    private DomainEventConsumer<WorkoutLoggedPayload> workoutLoggedConsumer;
    @Autowired
    private DomainEventConsumer<WorkoutUpdatedPayload> workoutUpdatedConsumer;
    @Autowired
    private DomainEventConsumer<WorkoutDeletedPayload> workoutDeletedConsumer;
    @Autowired
    private AnalyticsUserEnergyProfileRepository energyRepository;
    @Autowired
    private AnalyticsMealContributionRepository mealRepository;
    @Autowired
    private AnalyticsWorkoutContributionRepository workoutContributionRepository;
    @Autowired
    private AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;
    @Autowired
    private AnalyticsRebuildService rebuildService;
    @Autowired
    private AccountDeletionProjectionService accountDeletionService;
    @Autowired
    private ApplicationContext applicationContext;

    // ---- EnergyProfileComputed -------------------------------------------------------------------

    @Test
    void anEventWithoutAnOccurrenceTimeIsStampedFromTheInjectedClock() {
        // The processed-event row used the system clock here (and in BodyMetricsRecorded) while every other
        // projection used the injected one; all of them now share one recorder.
        AnalyticsProcessedEventRepository processedEvents = mock(AnalyticsProcessedEventRepository.class);
        Instant fixedNow = Instant.parse("2020-01-02T03:04:05Z");
        EnergyProfileComputedEventConsumerImpl consumer = new EnergyProfileComputedEventConsumerImpl(
                processedEvents, mock(AnalyticsUserEnergyProfileRepository.class), Clock.fixed(fixedNow, ZoneOffset.UTC));

        consumer.consume(new FitSyncDomainEvent<>(UUID.randomUUID(), "EnergyProfileComputed", 1, null, "user-service",
                UUID.randomUUID(), new EnergyProfileComputedPayload(null, null, null, null)));

        ArgumentCaptor<AnalyticsProcessedEventEntity> saved = ArgumentCaptor.forClass(AnalyticsProcessedEventEntity.class);
        verify(processedEvents).save(saved.capture());
        assertThat(saved.getValue().getProcessedAt()).isEqualTo(fixedNow);
    }

    @Test
    void energyProfileEventIsKeptAsHistoryAndAReplayAddsNothing() {
        UUID userId = UUID.randomUUID();
        Instant first = Instant.parse("2026-09-01T08:00:00Z");
        Instant second = Instant.parse("2026-09-20T08:00:00Z");
        FitSyncDomainEvent<EnergyProfileComputedPayload> one = energy(userId, 1662, true, first);
        FitSyncDomainEvent<EnergyProfileComputedPayload> two = energy(userId, 1700, false, second);

        energyConsumer.consume(one);
        energyConsumer.consume(one);
        energyConsumer.consume(two);

        assertThat(energyRepository.findAll().stream().filter(row -> userId.equals(row.getUserId()))).hasSize(2);
        assertThat(processedEventRepository.existsById(one.eventId())).isTrue();
        AnalyticsUserEnergyProfileEntity newest = energyRepository
                .findFirstByUserIdAndComputedAtBeforeOrderByComputedAtDesc(userId, Instant.parse("2026-10-01T00:00:00Z"))
                .orElseThrow();
        assertThat(newest.getBmrKcal()).isEqualTo(1700);
        assertThat(newest.isEstimated()).isFalse();
        AnalyticsUserEnergyProfileEntity earliest = energyRepository.findFirstByUserIdOrderByComputedAtAsc(userId)
                .orElseThrow();
        assertThat(earliest.getBmrKcal()).isEqualTo(1662);
        assertThat(earliest.isEstimated()).isTrue();
    }

    @Test
    void anOldEnergyProfileEventWithoutTheEstimatedFlagReadsAsNotEstimated() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EnergyProfileComputedPayload payload = mapper.readValue(
                "{\"bmrCalories\":1662,\"tdeeCalories\":2576,\"activityLevel\":\"MODERATE\","
                        + "\"computedAt\":\"2026-06-15T07:30:00Z\"}",
                EnergyProfileComputedPayload.class);
        assertThat(payload.estimated()).isFalse();
        assertThat(payload.bmrCalories()).isEqualTo(1662);
    }

    @Test
    void anEnergyProfileEventWithoutABmrIsRecordedButStoresNoRow() {
        UUID userId = UUID.randomUUID();
        FitSyncDomainEvent<EnergyProfileComputedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(), "EnergyProfileComputed", 1, Instant.parse("2026-09-01T08:00:00Z"), "user-service",
                userId, new EnergyProfileComputedPayload(null, null, null, Instant.parse("2026-09-01T08:00:00Z")));

        energyConsumer.consume(event);

        assertThat(energyRepository.findFirstByUserIdOrderByComputedAtAsc(userId)).isEmpty();
        assertThat(processedEventRepository.existsById(event.eventId())).isTrue();
    }

    @Test
    void theEnergyListenerBeanExistsOnlyWhenItsFlagIsOn() {
        // The test profile leaves every listener flag at its default of false.
        assertThat(applicationContext.getBeansOfType(EnergyProfileComputedKafkaListener.class)).isEmpty();
    }

    // ---- meal type ---------------------------------------------------------------------------------

    @Test
    void mealTypeIsStoredOnLogAndFollowsAnUpdate() {
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant consumedAt = today.atStartOfDay().plusHours(8).toInstant(ZoneOffset.UTC);

        mealLoggedConsumer.consume(new FitSyncDomainEvent<>(UUID.randomUUID(), "MealLogged", 1,
                consumedAt.plusSeconds(60), "nutrition-service", userId,
                new MealLoggedPayload(mealId, "breakfast", consumedAt, 500, new BigDecimal("30.00"),
                        new BigDecimal("50.00"), new BigDecimal("10.00"), 1, "MANUAL")));
        assertThat(mealRepository.findById(mealId).orElseThrow().getMealType()).isEqualTo("BREAKFAST");

        mealUpdatedConsumer.consume(new FitSyncDomainEvent<>(UUID.randomUUID(), "MealUpdated", 1,
                consumedAt.plusSeconds(120), "nutrition-service", userId,
                new MealUpdatedPayload(mealId, "SNACK", consumedAt, 250, new BigDecimal("10.00"),
                        new BigDecimal("20.00"), new BigDecimal("5.00"), 1, "MANUAL")));
        assertThat(mealRepository.findById(mealId).orElseThrow().getMealType()).isEqualTo("SNACK");
    }

    @Test
    void aMealWithoutATypeKeepsItNull() {
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        Instant consumedAt = LocalDate.now(ZoneOffset.UTC).atStartOfDay().plusHours(9).toInstant(ZoneOffset.UTC);

        mealLoggedConsumer.consume(new FitSyncDomainEvent<>(UUID.randomUUID(), "MealLogged", 1,
                consumedAt.plusSeconds(60), "nutrition-service", userId,
                new MealLoggedPayload(mealId, null, consumedAt, 300, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        1, "MANUAL")));

        assertThat(mealRepository.findById(mealId).orElseThrow().getMealType()).isNull();
    }

    // ---- estimated workout calories ---------------------------------------------------------------

    @Test
    void estimatedWorkoutCaloriesAreKeptRightOnUpdateAndDelete() {
        UUID userId = UUID.randomUUID();
        UUID estimatedSession = UUID.randomUUID();
        UUID givenSession = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startedAt = today.atStartOfDay().plusHours(6).toInstant(ZoneOffset.UTC);

        // null caloriesEstimated reads as false.
        workoutLoggedConsumer.consume(logged(userId, givenSession, startedAt, 200, null));
        assertThat(dailyEstimated(userId, today)).isZero();

        workoutLoggedConsumer.consume(logged(userId, estimatedSession, startedAt.plusSeconds(7200), 300, true));
        assertThat(dailyEstimated(userId, today)).isEqualTo(300);
        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today).orElseThrow()
                .getTotalCaloriesBurned()).isEqualTo(500);

        // The person types their own number: the day stops counting those calories as estimated.
        workoutUpdatedConsumer.consume(updated(userId, estimatedSession, startedAt.plusSeconds(7200), 350, false));
        assertThat(dailyEstimated(userId, today)).isZero();
        assertThat(workoutContributionRepository.findById(estimatedSession).orElseThrow().isCaloriesEstimated())
                .isFalse();

        // A later update flags it again with the new value.
        workoutUpdatedConsumer.consume(updated(userId, estimatedSession, startedAt.plusSeconds(7200), 400, true));
        assertThat(dailyEstimated(userId, today)).isEqualTo(400);

        workoutDeletedConsumer.consume(new FitSyncDomainEvent<>(UUID.randomUUID(), "WorkoutDeleted", 1,
                startedAt.plusSeconds(9000), "workout-service", userId,
                new WorkoutDeletedPayload(estimatedSession, null, startedAt.plusSeconds(9000))));
        assertThat(dailyEstimated(userId, today)).isZero();
        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today).orElseThrow()
                .getTotalCaloriesBurned()).isEqualTo(200);
    }

    @Test
    void rebuildKeepsTheEstimatedCaloriesOfTheDay() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startedAt = today.atStartOfDay().plusHours(6).toInstant(ZoneOffset.UTC);
        workoutLoggedConsumer.consume(logged(userId, UUID.randomUUID(), startedAt, 200, false));
        workoutLoggedConsumer.consume(logged(userId, UUID.randomUUID(), startedAt.plusSeconds(3600), 320, true));

        rebuildService.rebuildWorkouts(userId, today);

        assertThat(dailyEstimated(userId, today)).isEqualTo(320);
        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today).orElseThrow()
                .getTotalCaloriesBurned()).isEqualTo(520);
    }

    // ---- account deletion --------------------------------------------------------------------------

    @Test
    void accountDeletionPurgesTheEnergyHistoryOfThatUserOnly() {
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        energyConsumer.consume(energy(leaving, 1600, false, Instant.parse("2026-09-01T08:00:00Z")));
        energyConsumer.consume(energy(staying, 1900, false, Instant.parse("2026-09-01T08:00:00Z")));

        accountDeletionService.deleteUserData(new FitSyncDomainEvent<>(UUID.randomUUID(), "AccountDeleted", 1,
                Instant.parse("2026-09-30T08:00:00Z"), "user-service", leaving,
                new AccountDeletedPayload(Instant.parse("2026-09-30T08:00:00Z"), "USER_REQUESTED")));

        assertThat(energyRepository.findFirstByUserIdOrderByComputedAtAsc(leaving)).isEmpty();
        assertThat(energyRepository.findFirstByUserIdOrderByComputedAtAsc(staying)).isPresent();
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private int dailyEstimated(UUID userId, LocalDate date) {
        return dailyWorkoutRepository.findByUserIdAndActivityDate(userId, date).orElseThrow()
                .getEstimatedCaloriesBurned();
    }

    private static FitSyncDomainEvent<EnergyProfileComputedPayload> energy(UUID userId, int bmr, boolean estimated,
                                                                           Instant computedAt) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "EnergyProfileComputed", 1, computedAt, "user-service",
                userId, new EnergyProfileComputedPayload(bmr, bmr * 3 / 2, "MODERATE", computedAt, estimated));
    }

    private static FitSyncDomainEvent<WorkoutLoggedPayload> logged(UUID userId, UUID sessionId, Instant startedAt,
                                                                   int calories, Boolean estimated) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "WorkoutLogged", 1, startedAt.plusSeconds(60),
                "workout-service", userId,
                new WorkoutLoggedPayload(sessionId, null, "CARDIO", "COMPLETED", startedAt,
                        startedAt.plusSeconds(1800), 30, calories, 0, "MANUAL", estimated));
    }

    private static FitSyncDomainEvent<WorkoutUpdatedPayload> updated(UUID userId, UUID sessionId, Instant startedAt,
                                                                     int calories, Boolean estimated) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "WorkoutUpdated", 1, startedAt.plusSeconds(600),
                "workout-service", userId,
                new WorkoutUpdatedPayload(sessionId, null, "CARDIO", "COMPLETED", startedAt,
                        startedAt.plusSeconds(1800), 30, calories, 0, "MANUAL", estimated));
    }
}
