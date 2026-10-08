package com.fitsync.analytics.messaging;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.analytics.service.GoalProgressEvaluator;
import com.fitsync.analytics.service.NutritionAdherence;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects meals into the per-day nutrition rows and the dashboard snapshot's "today" fields. Each meal is
 * kept as a contribution row, so an update subtracts the old contribution from its day and adds the new one,
 * and a delete subtracts it. Events are applied once through the processed-event table, and a delete that
 * arrives before its meal leaves a marker so the late log is ignored.
 */
@Service
public class MealNutritionProjectionService {

    private static final int MEAL_TYPE_MAX_LENGTH = 20;
    /**
     * The processed-events row a MealDeleted leaves, keyed by the meal id, when it finds no contribution to
     * remove: a MealLogged or MealUpdated of that meal arriving afterwards is then ignored, so the outcome does
     * not depend on the order the two events arrive in.
     */
    static final String DELETED_MEAL_MARKER = "MealDeleted:meal";

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsMealContributionRepository mealContributionRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;
    private final GoalProgressEvaluator goalProgressEvaluator;
    private final Clock clock;

    public MealNutritionProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                          AnalyticsMealContributionRepository mealContributionRepository,
                                          AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                          AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                          AnalyticsCacheInvalidationService cacheInvalidationService,
                                          DashboardSnapshotInitializer dashboardSnapshotInitializer,
                                          GoalProgressEvaluator goalProgressEvaluator,
                                          Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.mealContributionRepository = mealContributionRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
        this.goalProgressEvaluator = goalProgressEvaluator;
        this.clock = clock;
    }

    @Transactional
    public void upsertMeal(FitSyncDomainEvent<?> event,
                           UUID mealId,
                           String mealType,
                           Instant consumedAt,
                           Integer totalCalories,
                           BigDecimal totalProteinGrams,
                           BigDecimal totalCarbsGrams,
                           BigDecimal totalFatGrams,
                           Integer itemCount,
                           Map<String, Integer> foodGroupCalories) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (mealId == null || consumedAt == null || isDeletedMeal(mealId)) {
            ProcessedEvents.record(processedEventRepository, event, clock);
            return;
        }

        LocalDate newDate = goalProgressEvaluator.activityDateForUser(event.userId(), consumedAt);
        AnalyticsMealContributionEntity existingContribution = mealContributionRepository.findById(mealId).orElse(null);
        LocalDate existingDate = existingContribution == null ? null : existingContribution.getActivityDate();

        if (existingContribution != null) {
            applyContributionDelta(event.userId(), existingContribution.getActivityDate(), existingContribution, null, event.occurredAt());
        }

        AnalyticsMealContributionEntity newContribution = existingContribution != null
                ? existingContribution
                : new AnalyticsMealContributionEntity();
        newContribution.setMealId(mealId);
        newContribution.setUserId(event.userId());
        newContribution.setActivityDate(newDate);
        newContribution.setMealType(normalizeMealType(mealType));
        newContribution.setTotalCalories(totalCalories);
        newContribution.setTotalProteinGrams(totalProteinGrams);
        newContribution.setTotalCarbsGrams(totalCarbsGrams);
        newContribution.setTotalFatGrams(totalFatGrams);
        newContribution.setItemCount(defaultInt(itemCount));
        newContribution.setFoodGroupCalories(writeGroups(foodGroupCalories));
        newContribution.setLastAppliedAt(resolveOccurredAt(event.occurredAt(), consumedAt));
        mealContributionRepository.save(newContribution);

        applyContributionDelta(event.userId(), newDate, null, newContribution, event.occurredAt());
        recomputeNutritionGoalsForToday(event.userId(), existingDate, newDate);
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(event.userId());
    }

    @Transactional
    public void deleteMeal(FitSyncDomainEvent<?> event, UUID mealId) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (mealId == null) {
            ProcessedEvents.record(processedEventRepository, event, clock);
            return;
        }

        AnalyticsMealContributionEntity existingContribution = mealContributionRepository.findById(mealId).orElse(null);
        if (existingContribution != null) {
            applyContributionDelta(event.userId(), existingContribution.getActivityDate(), existingContribution, null, event.occurredAt());
            mealContributionRepository.delete(existingContribution);
            recomputeNutritionGoalsForToday(event.userId(), existingContribution.getActivityDate(), null);
        } else if (!processedEventRepository.existsById(mealId)) {
            rememberDeletedMeal(event, mealId);
        }
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(event.userId());
    }

    private void applyContributionDelta(UUID userId,
                                        LocalDate activityDate,
                                        AnalyticsMealContributionEntity oldContribution,
                                        AnalyticsMealContributionEntity newContribution,
                                        Instant occurredAt) {
        AnalyticsUserDailyNutritionEntity daily = dailyNutritionRepository.findByUserIdAndActivityDate(userId, activityDate)
                .orElseGet(() -> createDailyNutritionRow(userId, activityDate));

        int nextMealCount = defaultInt(daily.getMealCount())
                - contributionCount(oldContribution)
                + contributionCount(newContribution);
        int nextCalories = defaultInt(daily.getTotalCaloriesConsumed())
                - contributionCalories(oldContribution)
                + contributionCalories(newContribution);
        BigDecimal nextProtein = defaultDecimal(daily.getTotalProteinGrams())
                .subtract(contributionProtein(oldContribution))
                .add(contributionProtein(newContribution));
        BigDecimal nextCarbs = defaultDecimal(daily.getTotalCarbsGrams())
                .subtract(contributionCarbs(oldContribution))
                .add(contributionCarbs(newContribution));
        BigDecimal nextFat = defaultDecimal(daily.getTotalFatGrams())
                .subtract(contributionFat(oldContribution))
                .add(contributionFat(newContribution));

        daily.setMealCount(Math.max(nextMealCount, 0));
        daily.setTotalCaloriesConsumed(Math.max(nextCalories, 0));
        daily.setTotalProteinGrams(nextProtein.max(BigDecimal.ZERO));
        daily.setTotalCarbsGrams(nextCarbs.max(BigDecimal.ZERO));
        daily.setTotalFatGrams(nextFat.max(BigDecimal.ZERO));
        daily.setProjectionUpdatedAt(resolveOccurredAt(occurredAt, null));
        applyFoodGroupDelta(daily, oldContribution, newContribution);
        NutritionAdherence.recalculate(daily);
        dailyNutritionRepository.save(daily);

        syncDashboardSnapshot(userId, activityDate, daily, occurredAt);
    }

    /**
     * Delta-adjusts the day's food-group energy aggregate: subtract the removed meal's per-group snapshot,
     * add the new one. Groups that reach zero are dropped. Encoded as "CODE=kcal;CODE=kcal" (AN-1).
     */
    private void applyFoodGroupDelta(AnalyticsUserDailyNutritionEntity daily,
                                     AnalyticsMealContributionEntity oldContribution,
                                     AnalyticsMealContributionEntity newContribution) {
        Map<String, Integer> aggregate = parseGroups(daily.getFoodGroupCalories());
        Map<String, Integer> oldGroups = parseGroups(
                oldContribution == null ? null : oldContribution.getFoodGroupCalories());
        Map<String, Integer> newGroups = parseGroups(
                newContribution == null ? null : newContribution.getFoodGroupCalories());
        Set<String> codes = new HashSet<>(oldGroups.keySet());
        codes.addAll(newGroups.keySet());
        for (String code : codes) {
            int next = aggregate.getOrDefault(code, 0)
                    - oldGroups.getOrDefault(code, 0)
                    + newGroups.getOrDefault(code, 0);
            if (next > 0) {
                aggregate.put(code, next);
            } else {
                aggregate.remove(code);
            }
        }
        daily.setFoodGroupCalories(writeGroups(aggregate));
    }

    /** Keeps the meal type as sent, upper-cased and capped; blank means unknown and stays null. */
    private static String normalizeMealType(String mealType) {
        if (mealType == null || mealType.isBlank()) {
            return null;
        }
        String normalized = mealType.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.length() > MEAL_TYPE_MAX_LENGTH ? normalized.substring(0, MEAL_TYPE_MAX_LENGTH) : normalized;
    }

    private static String writeGroups(Map<String, Integer> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        String encoded = groups.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() != 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        return encoded.isEmpty() ? null : encoded;
    }

    private static Map<String, Integer> parseGroups(String encoded) {
        Map<String, Integer> groups = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return groups;
        }
        for (String part : encoded.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                groups.put(part.substring(0, separator), Integer.parseInt(part.substring(separator + 1)));
            }
        }
        return groups;
    }

    private AnalyticsUserDailyNutritionEntity createDailyNutritionRow(UUID userId, LocalDate activityDate) {
        AnalyticsUserDailyNutritionEntity entity = new AnalyticsUserDailyNutritionEntity();
        entity.setUserId(userId);
        entity.setActivityDate(activityDate);
        entity.setMealCount(0);
        entity.setTotalCaloriesConsumed(0);
        entity.setTotalProteinGrams(BigDecimal.ZERO);
        entity.setTotalCarbsGrams(BigDecimal.ZERO);
        entity.setTotalFatGrams(BigDecimal.ZERO);

        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(userId).orElse(null);
        if (snapshot != null) {
            entity.setCalorieTarget(snapshot.getTodayCaloriesTarget());
            entity.setProteinTargetGrams(snapshot.getTodayProteinTargetGrams());
        }
        entity.setProjectionUpdatedAt(Instant.now(clock));
        NutritionAdherence.recalculate(entity);
        return entity;
    }

    private void syncDashboardSnapshot(UUID userId,
                                       LocalDate activityDate,
                                       AnalyticsUserDailyNutritionEntity daily,
                                       Instant occurredAt) {
        LocalDate today = goalProgressEvaluator.todayForUser(userId);
        if (!today.equals(activityDate)) {
            return;
        }
        AnalyticsUserDashboardSnapshotEntity snapshot = findOrCreateDashboardSnapshot(userId, occurredAt);
        snapshot.setTodayCaloriesConsumed(daily.getTotalCaloriesConsumed());
        snapshot.setTodayProteinGrams(daily.getTotalProteinGrams());
        snapshot.setGeneratedAt(resolveOccurredAt(occurredAt, null));
        dashboardSnapshotRepository.save(snapshot);
    }

    private void recomputeNutritionGoalsForToday(UUID userId, LocalDate oldDate, LocalDate newDate) {
        LocalDate today = goalProgressEvaluator.todayForUser(userId);
        if (today.equals(oldDate) || today.equals(newDate)) {
            goalProgressEvaluator.recompute(userId, GoalProgressEvaluator.NUTRITION_GOAL_TYPES, today);
        }
    }

    private AnalyticsUserDashboardSnapshotEntity findOrCreateDashboardSnapshot(UUID userId, Instant occurredAt) {
        dashboardSnapshotInitializer.insertIfMissing(userId, resolveOccurredAt(occurredAt, null));
        return dashboardSnapshotRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));
    }

    private int contributionCount(AnalyticsMealContributionEntity contribution) {
        return contribution == null ? 0 : 1;
    }

    private int contributionCalories(AnalyticsMealContributionEntity contribution) {
        return contribution == null || contribution.getTotalCalories() == null ? 0 : contribution.getTotalCalories();
    }

    private BigDecimal contributionProtein(AnalyticsMealContributionEntity contribution) {
        return contribution == null ? BigDecimal.ZERO : defaultDecimal(contribution.getTotalProteinGrams());
    }

    private BigDecimal contributionCarbs(AnalyticsMealContributionEntity contribution) {
        return contribution == null ? BigDecimal.ZERO : defaultDecimal(contribution.getTotalCarbsGrams());
    }

    private BigDecimal contributionFat(AnalyticsMealContributionEntity contribution) {
        return contribution == null ? BigDecimal.ZERO : defaultDecimal(contribution.getTotalFatGrams());
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Instant resolveOccurredAt(Instant occurredAt, Instant fallback) {
        if (occurredAt != null) {
            return occurredAt;
        }
        if (fallback != null) {
            return fallback;
        }
        return Instant.now(clock);
    }

    private boolean isDeletedMeal(UUID mealId) {
        return processedEventRepository.findById(mealId)
                .map(row -> DELETED_MEAL_MARKER.equals(row.getEventType()))
                .orElse(false);
    }

    private void rememberDeletedMeal(FitSyncDomainEvent<?> event, UUID mealId) {
        AnalyticsProcessedEventEntity marker = new AnalyticsProcessedEventEntity();
        marker.setEventId(mealId);
        marker.setEventType(DELETED_MEAL_MARKER);
        marker.setUserId(event.userId());
        marker.setProcessedAt(resolveOccurredAt(event.occurredAt(), null));
        processedEventRepository.save(marker);
    }
}
