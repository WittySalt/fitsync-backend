package com.fitsync.recommendation.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import com.fitsync.recommendation.coach.reading.CoachPageReadingEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.reading.ReadingOutcome;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.repository.RecommendationProcessedEventRepository;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationContextProjectionTests {

    @Autowired
    private GoalUpdatedEventHandler goalUpdatedEventHandler;

    @Autowired
    private BodyMetricsRecordedEventHandler bodyMetricsRecordedEventHandler;

    @Autowired
    private MealLoggedEventHandler mealLoggedEventHandler;

    @Autowired
    private MealDeletedEventHandler mealDeletedEventHandler;

    @Autowired
    private PersonalRecordAchievedEventHandler personalRecordAchievedEventHandler;

    @Autowired
    private EnergyProfileComputedEventHandler energyProfileComputedEventHandler;

    @Autowired
    private UserRegisteredEventHandler userRegisteredEventHandler;

    @Autowired
    private UserProfileUpdatedEventHandler userProfileUpdatedEventHandler;

    @Autowired
    private UserPreferencesUpdatedEventHandler userPreferencesUpdatedEventHandler;

    @Autowired
    private WeeklyRecapGeneratedEventHandler weeklyRecapGeneratedEventHandler;

    @Autowired
    private StreakMilestoneReachedEventHandler streakMilestoneReachedEventHandler;

    @Autowired
    private AccountDeletedEventHandler accountDeletedEventHandler;

    @Autowired
    private RecommendationContextRepository contextRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @Autowired
    private RecommendationProcessedEventRepository processedEventRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void goalUpdatedProjectsGoalAndArchivalClearsIt() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
        assertThat(context.getCurrentGoalTargetValue()).isEqualByComparingTo("75");
        assertThat(context.getCurrentGoalTargetDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(context.isStale()).isTrue();

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "CANCELLED")));

        context = context();
        assertThat(context.getCurrentGoalType()).isNull();
        assertThat(context.getCurrentGoalTargetValue()).isNull();
        assertThat(context.getCurrentGoalTargetDate()).isNull();
    }

    @Test
    void proteinGoalProjectsIntoItsOwnFieldAndLeavesTheCurrentGoalSlotAlone() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("90.00"), null, null,
                Instant.parse("2026-07-01T08:00:00Z"))));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "PROTEIN", new BigDecimal("140.000"), "g",
                null, "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getProteinTargetGrams()).isEqualByComparingTo("140.000");
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
        assertThat(context.getCurrentGoalTargetValue()).isEqualByComparingTo("75");
    }

    @Test
    void workoutFrequencyGoalProjectsIntoItsOwnFieldAndLeavesTheCurrentGoalSlotAlone() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WORKOUT_FREQUENCY", new BigDecimal("4.000"), "days",
                null, "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getWeeklyWorkoutGoalDays()).isEqualTo(4);
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
        assertThat(context.getCurrentGoalTargetValue()).isEqualByComparingTo("75");
    }

    @Test
    void calorieGoalProjectsIntoItsOwnFieldAndLeavesTheCurrentGoalSlotAlone() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "CALORIES", new BigDecimal("2100.000"), "kcal",
                null, "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getCalorieGoalKcal()).isEqualTo(2100);
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
        assertThat(context.getCurrentGoalTargetValue()).isEqualByComparingTo("75");
        assertThat(context.isStale()).isTrue();
    }

    @Test
    void archivedCalorieGoalClearsOnlyTheCalorieGoal() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "CALORIES", new BigDecimal("2100.000"), "kcal",
                null, "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "CALORIES", new BigDecimal("2100.000"), "kcal",
                null, "ARCHIVED")));

        RecommendationContextEntity context = context();
        assertThat(context.getCalorieGoalKcal()).isNull();
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
    }

    @Test
    void archivedProteinGoalClearsOnlyTheProteinTarget() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "PROTEIN", new BigDecimal("140.000"), "g",
                null, "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "PROTEIN", new BigDecimal("140.000"), "g",
                null, "ARCHIVED")));

        RecommendationContextEntity context = context();
        assertThat(context.getProteinTargetGrams()).isNull();
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
    }

    @Test
    void archivedGoalForADifferentTypeThanTheSlotLeavesTheSlotAlone() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "BODY_FAT", new BigDecimal("15.000"), "%",
                LocalDate.parse("2026-10-01"), "ARCHIVED")));

        RecommendationContextEntity context = context();
        assertThat(context.getCurrentGoalType()).isEqualTo("WEIGHT");
        assertThat(context.getCurrentGoalTargetValue()).isEqualByComparingTo("75");
    }

    @Test
    void archivedGoalMatchingTheSlotClearsTheSlotAndBaseline() {
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("90.00"), null, null,
                Instant.parse("2026-07-01T08:00:00Z"))));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("75.000"), "kg",
                LocalDate.parse("2026-10-01"), "ARCHIVED")));

        RecommendationContextEntity context = context();
        assertThat(context.getCurrentGoalType()).isNull();
        assertThat(context.getCurrentGoalTargetValue()).isNull();
        assertThat(context.getGoalBaselineWeightKg()).isNull();
        assertThat(context.getGoalBaselineOn()).isNull();
    }

    @Test
    void weightGoalTakesTheWeighInOfItsStartDayAsTheBaseline() {
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("90.00"), null, null,
                Instant.parse("2026-07-06T07:00:00Z"))));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("80.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getGoalBaselineWeightKg()).isEqualByComparingTo("90.00");
        assertThat(context.getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-07-06"));
    }

    @Test
    void aWeighInFromBeforeTheGoalIsNotItsBaselineButTheFirstOneAfterIs() {
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("91.00"), null, null,
                Instant.parse("2026-07-01T08:00:00Z"))));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("80.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));

        RecommendationContextEntity pending = context();
        assertThat(pending.getGoalBaselineWeightKg()).isNull();
        assertThat(pending.getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-07-06"));

        // A back-filled weigh-in from before the start does not count.
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("92.00"), null, null,
                Instant.parse("2026-07-03T08:00:00Z"))));
        assertThat(context().getGoalBaselineWeightKg()).isNull();

        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("89.80"), null, null,
                Instant.parse("2026-07-07T08:00:00Z"))));
        RecommendationContextEntity context = context();
        assertThat(context.getGoalBaselineWeightKg()).isEqualByComparingTo("89.80");
        assertThat(context.getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-07-07"));
    }

    @Test
    void anUpdateOfTheSameGoalKeepsTheStartWeightWhileLaterWeighInsMoveOnlyTheLatest() {
        UUID goalId = UUID.randomUUID();
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("74.20"), null, null,
                Instant.parse("2026-07-06T07:00:00Z"))));
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                goalId, "WEIGHT", new BigDecimal("68.000"), "kg", LocalDate.parse("2026-12-11"), "ACTIVE")));
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("71.20"), null, null,
                Instant.parse("2026-08-20T07:00:00Z"))));

        // The same goal edited, or its event sent again, after the weight has moved.
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                goalId, "WEIGHT", new BigDecimal("68.000"), "kg", LocalDate.parse("2026-12-11"), "ACTIVE")));

        RecommendationContextEntity context = context();
        assertThat(context.getCurrentGoalId()).isEqualTo(goalId);
        assertThat(context.getGoalBaselineWeightKg()).isEqualByComparingTo("74.20");
        assertThat(context.getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(context.getLatestWeightKg()).isEqualByComparingTo("71.20");

        // A new goal starts a new baseline from the weigh-in on or after its start.
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("66.000"), "kg", LocalDate.parse("2027-03-01"),
                "ACTIVE")));
        assertThat(context().getGoalBaselineWeightKg()).isEqualByComparingTo("71.20");
        assertThat(context().getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-08-20"));
    }

    @Test
    void goalSetBeforeAnyWeightGetsItsBaselineFromTheFirstMeasurement() {
        goalUpdatedEventHandler.consume(event("GoalUpdated", new GoalUpdatedPayload(
                UUID.randomUUID(), "WEIGHT", new BigDecimal("80.000"), "kg",
                LocalDate.parse("2026-10-01"), "ACTIVE")));
        assertThat(context().getGoalBaselineWeightKg()).isNull();

        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("89.50"), null, null,
                Instant.parse("2026-07-08T08:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.getGoalBaselineWeightKg()).isEqualByComparingTo("89.50");
        assertThat(context.getGoalBaselineOn()).isEqualTo(LocalDate.parse("2026-07-08"));
    }

    @Test
    void bodyMetricsKeepLatestMeasurementAndIgnoreOlderOnes() {
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("82.50"), new BigDecimal("21.00"), null,
                Instant.parse("2026-07-05T08:00:00Z"))));
        bodyMetricsRecordedEventHandler.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(
                UUID.randomUUID(), new BigDecimal("99.99"), null, null,
                Instant.parse("2026-07-01T08:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.getLatestWeightKg()).isEqualByComparingTo("82.50");
        assertThat(context.getLatestBodyFatRate()).isEqualByComparingTo("21.00");
        assertThat(context.getBodyMetricsRecordedAt()).isEqualTo(Instant.parse("2026-07-05T08:00:00Z"));
    }

    @Test
    void mealLoggedKeepsTheLatestMealDayAndWritesNoRunningTotals() {
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-06T08:00:00Z"), 400, new BigDecimal("25.00"))));
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-06T12:30:00Z"), 650, new BigDecimal("40.50"))));

        RecommendationContextEntity context = context();
        assertThat(context.getTodayDate()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(context.getTodayCaloriesConsumed()).isNull();
        assertThat(context.getTodayProteinGrams()).isNull();
        assertThat(context.isStale()).isTrue();

        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-07T07:00:00Z"), 300, new BigDecimal("18.00"))));
        // A late-arriving historical meal does not move the tracked day back.
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-05T09:00:00Z"), 999, new BigDecimal("99.00"))));

        context = context();
        assertThat(context.getTodayDate()).isEqualTo(LocalDate.parse("2026-07-07"));
        assertThat(context.getTodayCaloriesConsumed()).isNull();
    }

    @Test
    void mealDatesRespectTheUserTimezone() {
        userPreferencesUpdatedEventHandler.consume(event("UserPreferencesUpdated",
                new UserPreferencesUpdatedPayload("METRIC", 4, "NONE", true,
                        List.of(), List.of(), "Asia/Shanghai")));

        // 17:00 UTC on the 6th is already 01:00 on the 7th in Shanghai.
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-06T17:00:00Z"), 500, new BigDecimal("30.00"))));

        assertThat(context().getTodayDate()).isEqualTo(LocalDate.parse("2026-07-07"));
    }

    @Test
    void mealLoggedAccumulatesFoodGroupsOverTheWeekAndResetsOnANewWeek() {
        // 2026-07-06 is a Monday.
        mealLoggedEventHandler.consume(event("MealLogged", mealWithGroups(
                Instant.parse("2026-07-06T08:00:00Z"), Map.of("MEAT", 300, "GRAIN", 100))));
        mealLoggedEventHandler.consume(event("MealLogged", mealWithGroups(
                Instant.parse("2026-07-09T12:00:00Z"), Map.of("MEAT", 200, "VEGETABLE", 50))));

        RecommendationContextEntity context = context();
        assertThat(context.getNutritionWeekStart()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(context.getWeekFoodGroupCaloriesJson())
                .isEqualTo("{\"GRAIN\":100,\"MEAT\":500,\"VEGETABLE\":50}");

        // A meal in the next week restarts the accumulation.
        mealLoggedEventHandler.consume(event("MealLogged", mealWithGroups(
                Instant.parse("2026-07-13T09:00:00Z"), Map.of("FISH", 400))));

        context = context();
        assertThat(context.getNutritionWeekStart()).isEqualTo(LocalDate.parse("2026-07-13"));
        assertThat(context.getWeekFoodGroupCaloriesJson()).isEqualTo("{\"FISH\":400}");
    }

    @Test
    void mealsWithoutClassifiedItemsLeaveTheWeekAccumulationAlone() {
        mealLoggedEventHandler.consume(event("MealLogged", mealWithGroups(
                Instant.parse("2026-07-06T08:00:00Z"), Map.of("MEAT", 300))));
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-07T08:00:00Z"), 500, new BigDecimal("30.00"))));

        RecommendationContextEntity context = context();
        assertThat(context.getNutritionWeekStart()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(context.getWeekFoodGroupCaloriesJson()).isEqualTo("{\"MEAT\":300}");
    }

    @Test
    void energyProfileComputedSetsThePreciseEnergyTarget() {
        energyProfileComputedEventHandler.consume(event("EnergyProfileComputed",
                new EnergyProfileComputedPayload(1814, 2812, "MODERATELY_ACTIVE",
                        Instant.parse("2026-07-06T10:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.getEnergyTargetCalories()).isEqualTo(2812);
        assertThat(context.isStale()).isTrue();
    }

    @Test
    void personalRecordAchievedKeepsOnlyTheLatestRecord() {
        personalRecordAchievedEventHandler.consume(event("PersonalRecordAchieved",
                new PersonalRecordAchievedPayload(UUID.randomUUID(), "Barbell Squat", UUID.randomUUID(),
                        List.of(new PersonalRecordAchievedPayload.RecordAchievement(
                                "MAX_WEIGHT_KG", new BigDecimal("120.00"))),
                        Instant.parse("2026-07-05T18:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.getLastPrExercise()).isEqualTo("Barbell Squat");
        assertThat(context.getLastPrRecordType()).isEqualTo("MAX_WEIGHT_KG");
        assertThat(context.getLastPrValue()).isEqualByComparingTo("120.00");
        assertThat(context.getLastPrAt()).isEqualTo(Instant.parse("2026-07-05T18:00:00Z"));
        assertThat(context.isStale()).isTrue();
    }

    @Test
    void mealDeletedOnlyMarksTheContextStale() {
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-06T08:00:00Z"), 400, new BigDecimal("25.00"))));
        mealDeletedEventHandler.consume(event("MealDeleted",
                new MealDeletedPayload(UUID.randomUUID(), Instant.parse("2026-07-06T09:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.isStale()).isTrue();
        assertThat(context.getTodayCaloriesConsumed()).isNull();
    }

    @Test
    void userRegisteredCreatesTheColdStartContextRow() {
        userRegisteredEventHandler.consume(event("UserRegistered", new UserRegisteredPayload(
                "user@example.com", "User", "USER", Instant.parse("2026-07-06T10:00:00Z"))));

        RecommendationContextEntity context = context();
        assertThat(context.isStale()).isTrue();
        assertThat(context.getCurrentGoalType()).isNull();
    }

    @Test
    void profileAndPreferencesProjectTdeeInputsAndPreferences() {
        userProfileUpdatedEventHandler.consume(event("UserProfileUpdated", new UserProfileUpdatedPayload(
                "User", new BigDecimal("181.5"), "MODERATE", true, List.of("heightCm"), "Europe/Berlin")));
        userPreferencesUpdatedEventHandler.consume(event("UserPreferencesUpdated",
                new UserPreferencesUpdatedPayload("METRIC", 4, "VEGETARIAN", true,
                        List.of("DUMBBELLS", "BENCH"), List.of("CHEST", "LEGS"), null)));

        RecommendationContextEntity context = context();
        assertThat(context.getHeightCm()).isEqualByComparingTo("181.5");
        assertThat(context.getActivityLevel()).isEqualTo("MODERATE");
        assertThat(context.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(context.getWorkoutDaysPerWeek()).isEqualTo(4);
        assertThat(context.getDietaryPreference()).isEqualTo("VEGETARIAN");
        assertThat(context.getEquipmentInventory()).isEqualTo("DUMBBELLS,BENCH");
        assertThat(context.getFocusAreas()).isEqualTo("CHEST,LEGS");
    }

    @Test
    void weeklyRecapAndStreakProjectTheAnalyticsFacts() {
        weeklyRecapGeneratedEventHandler.consume(event("WeeklyRecapGenerated",
                new WeeklyRecapGeneratedPayload(LocalDate.parse("2026-06-29"), LocalDate.parse("2026-07-05"),
                        4, 180, 1500, 12000, 4)));
        // An older recap must not replace the tracked week.
        weeklyRecapGeneratedEventHandler.consume(event("WeeklyRecapGenerated",
                new WeeklyRecapGeneratedPayload(LocalDate.parse("2026-06-22"), LocalDate.parse("2026-06-28"),
                        1, 30, 200, 9000, 1)));
        streakMilestoneReachedEventHandler.consume(event("StreakMilestoneReached",
                new StreakMilestoneReachedPayload(7, 7, LocalDate.parse("2026-07-05"))));

        RecommendationContextEntity context = context();
        assertThat(context.getRecentWeekStart()).isEqualTo(LocalDate.parse("2026-06-29"));
        assertThat(context.getRecentWorkoutCount()).isEqualTo(4);
        assertThat(context.getRecentWorkoutMinutes()).isEqualTo(180);
        assertThat(context.getRecentActiveDays()).isEqualTo(4);
        assertThat(context.getCurrentStreakDays()).isEqualTo(7);
    }

    @Test
    void duplicateEventsAreAppliedExactlyOnce() {
        FitSyncDomainEvent<MealLoggedPayload> event = event("MealLogged", meal(
                Instant.parse("2026-07-06T08:00:00Z"), 400, new BigDecimal("25.00")));

        mealLoggedEventHandler.consume(event);
        mealLoggedEventHandler.consume(event);

        assertThat(context().getTodayDate()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void accountDeletedPurgesEverythingButItsOwnLedgerRow() {
        mealLoggedEventHandler.consume(event("MealLogged", meal(
                Instant.parse("2026-07-06T08:00:00Z"), 400, new BigDecimal("25.00"))));
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setType("NUTRITION_GAP");
        result.setSource(RecommendationSource.RULE);
        result.setPayload("{}");
        result.setGeneratedAt(Instant.parse("2026-07-06T09:00:00Z"));
        resultRepository.saveAndFlush(result);

        CoachPageReadingEntity reading = new CoachPageReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setUserId(userId);
        reading.setSurface(ReadingSurface.WORKOUT);
        reading.setFactsHash("hash");
        reading.setOutcome(ReadingOutcome.ACCEPTED);
        reading.setStartedAt(Instant.parse("2026-07-06T09:00:00Z"));
        readingRepository.saveAndFlush(reading);

        FitSyncDomainEvent<AccountDeletedPayload> deletion = event("AccountDeleted",
                new AccountDeletedPayload(Instant.parse("2026-07-06T10:00:00Z"), "USER_REQUEST"));
        accountDeletedEventHandler.consume(deletion);

        assertThat(contextRepository.findById(userId)).isEmpty();
        assertThat(resultRepository.findByUserId(userId)).isEmpty();
        assertThat(readingRepository.findByUserIdAndSurface(userId, ReadingSurface.WORKOUT)).isEmpty();
        assertThat(processedEventRepository.findAll())
                .singleElement()
                .satisfies(processed -> assertThat(processed.getEventId()).isEqualTo(deletion.eventId()));
    }

    private RecommendationContextEntity context() {
        return contextRepository.findById(userId).orElseThrow();
    }

    private <T> FitSyncDomainEvent<T> event(String eventType, T payload) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), eventType, 1, Instant.parse("2026-07-06T12:00:00Z"),
                "test", userId, payload);
    }

    private static MealLoggedPayload meal(Instant consumedAt, Integer calories, BigDecimal protein) {
        return new MealLoggedPayload(UUID.randomUUID(), "LUNCH", consumedAt, calories, protein,
                new BigDecimal("50.00"), new BigDecimal("20.00"), 2, "MANUAL");
    }

    private static MealLoggedPayload mealWithGroups(Instant consumedAt, Map<String, Integer> foodGroupCalories) {
        return new MealLoggedPayload(UUID.randomUUID(), "LUNCH", consumedAt, 400, new BigDecimal("25.00"),
                new BigDecimal("50.00"), new BigDecimal("20.00"), 2, "MANUAL", foodGroupCalories);
    }
}
