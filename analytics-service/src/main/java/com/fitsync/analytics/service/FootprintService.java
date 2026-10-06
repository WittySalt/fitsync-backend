package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsFootprintResponse;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;

/**
 * The Me page footprint, from the daily workout and nutrition rollups. A day is logged when its rollup
 * holds a meal; a fast day is recorded as a meal of its own, so it is logged too.
 */
@Service
public class FootprintService {

    /** The grid never reaches further back than this, however old the first day is. */
    static final int MAX_YEARS = 10;

    private final AnalyticsUserDashboardSnapshotRepository snapshotRepository;
    private final AnalyticsUserDailyWorkoutRepository workoutRepository;
    private final AnalyticsUserDailyNutritionRepository nutritionRepository;
    private final AchievementsService achievementsService;
    private final Clock clock;

    public FootprintService(AnalyticsUserDashboardSnapshotRepository snapshotRepository,
                            AnalyticsUserDailyWorkoutRepository workoutRepository,
                            AnalyticsUserDailyNutritionRepository nutritionRepository,
                            AchievementsService achievementsService,
                            Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.workoutRepository = workoutRepository;
        this.nutritionRepository = nutritionRepository;
        this.achievementsService = achievementsService;
        this.clock = clock;
    }

    /**
     * @param memberSince the account's first day, from the caller's profile; null to start the grid at the
     *                    first day anything was logged
     */
    public AnalyticsFootprintResponse getFootprint(CurrentUserPrincipal principal, LocalDate memberSince) {
        UUID userId = principal.userId();
        LocalDate today = LocalDate.now(clock.withZone(zoneOf(userId)));
        LocalDate earliestAllowed = today.minusYears(MAX_YEARS);
        if (memberSince != null && (memberSince.isAfter(today) || memberSince.isBefore(earliestAllowed))) {
            throw new BadRequestException("since must be a past date within the last " + MAX_YEARS + " years");
        }

        Set<LocalDate> trained = new HashSet<>();
        int workouts = 0;
        for (AnalyticsUserDailyWorkoutEntity row : workoutRepository.findByUserIdOrderByActivityDateAsc(userId)) {
            int count = Objects.requireNonNullElse(row.getCompletedWorkoutCount(), 0);
            if (count > 0 && row.getActivityDate() != null && !row.getActivityDate().isAfter(today)) {
                trained.add(row.getActivityDate());
                workouts += count;
            }
        }
        Set<LocalDate> logged = new HashSet<>();
        for (AnalyticsUserDailyNutritionEntity row : nutritionRepository.findByUserIdOrderByActivityDateAsc(userId)) {
            if (Objects.requireNonNullElse(row.getMealCount(), 0) > 0 && row.getActivityDate() != null
                    && !row.getActivityDate().isAfter(today)) {
                logged.add(row.getActivityDate());
            }
        }

        LocalDate anchor = memberSince != null ? memberSince : earliest(trained, logged, today);
        if (anchor.isBefore(earliestAllowed)) {
            anchor = earliestAllowed;
        }
        LocalDate from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate to = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<AnalyticsFootprintResponse.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add(new AnalyticsFootprintResponse.Day(date, trained.contains(date), logged.contains(date)));
        }
        int badges = achievementsService.getAchievements(principal).earned();
        return new AnalyticsFootprintResponse(memberSince, from, to, days,
                new AnalyticsFootprintResponse.Totals(workouts, logged.size(), badges));
    }

    private static LocalDate earliest(Set<LocalDate> trained, Set<LocalDate> logged, LocalDate today) {
        LocalDate earliest = today;
        for (LocalDate date : trained) {
            earliest = date.isBefore(earliest) ? date : earliest;
        }
        for (LocalDate date : logged) {
            earliest = date.isBefore(earliest) ? date : earliest;
        }
        return earliest;
    }

    private ZoneId zoneOf(UUID userId) {
        String timezone = snapshotRepository.findById(userId)
                .map(AnalyticsUserDashboardSnapshotEntity::getTimezone)
                .orElse(null);
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }
}
