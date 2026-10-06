package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fitsync.nutrition.domain.InsightPeriod;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.MealProteinShareView;
import com.fitsync.nutrition.dto.NutritionPageInsightView;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealNutritionRow;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionPageInsightServiceImpl implements NutritionPageInsightService {

    // Named zone so the response says "UTC" rather than the offset id "Z".
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final Clock clock;
    private final MealNutritionQueryRepository mealNutritionQueryRepository;
    private final NutritionTargetRepository nutritionTargetRepository;

    public NutritionPageInsightServiceImpl(Clock clock,
                                           MealNutritionQueryRepository mealNutritionQueryRepository,
                                           NutritionTargetRepository nutritionTargetRepository) {
        this.clock = clock;
        this.mealNutritionQueryRepository = mealNutritionQueryRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionPageInsightView getNutritionPageInsight(CurrentUserPrincipal principal, String timezone) {
        return getNutritionPageInsight(principal, timezone, false);
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionPageInsightView getNutritionPageInsight(CurrentUserPrincipal principal, String timezone,
                                                            boolean thisWeek) {
        ZoneId zoneId = resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate periodStart = thisWeek ? thisMonday : thisMonday.minusWeeks(1);
        LocalDate periodEnd = thisWeek ? today : periodStart.plusDays(6);
        InsightPeriod described = thisWeek ? InsightPeriod.THIS_WEEK : InsightPeriod.PREVIOUS_WEEK;
        LocalDate earlierStart = periodStart.minusWeeks(1);

        // One query covers both weeks. The rows are then split by local date, because a meal belongs
        // to the day it was eaten in the caller's timezone and not to the UTC day.
        List<MealNutritionRow> rows = mealNutritionQueryRepository.findMealNutritionRows(
                principal.userId(),
                earlierStart.atStartOfDay(zoneId).toInstant(),
                periodEnd.plusDays(1).atStartOfDay(zoneId).toInstant());

        Week period = new Week();
        Week earlier = new Week();
        for (MealNutritionRow row : rows) {
            LocalDate date = LocalDate.ofInstant(row.getConsumedAt(), zoneId);
            if (!date.isBefore(periodStart) && !date.isAfter(periodEnd)) {
                period.add(date, row);
            } else if (!date.isBefore(earlierStart) && date.isBefore(periodStart)) {
                earlier.add(date, row);
            }
        }

        if (period.daysLogged() == 0) {
            return new NutritionPageInsightView(zoneId.getId(), InsightPeriod.NONE, periodStart, periodEnd,
                    0, null, null, null, null, null, null, List.of());
        }

        NutritionTargetEntity target = nutritionTargetRepository.findById(principal.userId()).orElse(null);
        int days = period.daysLogged();
        List<MealProteinShareView> byMeal = new ArrayList<>();
        // The enum runs breakfast, lunch, dinner and then the remaining types, which is the order of a day.
        for (MealType type : MealType.values()) {
            BigDecimal grams = type == MealType.FAST ? null : period.proteinByMeal.get(type);
            if (grams != null) {
                byMeal.add(new MealProteinShareView(type, labelOf(type), perDay(grams, days)));
            }
        }

        return new NutritionPageInsightView(
                zoneId.getId(),
                described,
                periodStart,
                periodEnd,
                days,
                perDay(period.protein, days),
                earlier.daysLogged() == 0 ? null : perDay(earlier.protein, earlier.daysLogged()),
                target == null ? null : target.getProteinTarget(),
                wholePerDay(period.calories, days),
                earlier.daysLogged() == 0 ? null : wholePerDay(earlier.calories, earlier.daysLogged()),
                target == null ? null : target.getCalorieTarget(),
                byMeal);
    }

    private static BigDecimal perDay(BigDecimal total, int days) {
        return total.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP);
    }

    private static Integer wholePerDay(long total, int days) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).intValueExact();
    }

    static String labelOf(MealType type) {
        String lower = type.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid timezone");
        }
    }

    /** Running totals for one week of meals. */
    private static final class Week {
        private final Set<LocalDate> loggedDates = new HashSet<>();
        private final Map<MealType, BigDecimal> proteinByMeal = new EnumMap<>(MealType.class);
        private BigDecimal protein = BigDecimal.ZERO;
        private long calories;

        void add(LocalDate date, MealNutritionRow row) {
            loggedDates.add(date);
            BigDecimal itemProtein = row.getProteinGrams() == null ? BigDecimal.ZERO : row.getProteinGrams();
            protein = protein.add(itemProtein);
            calories += row.getCalories() == null ? 0 : row.getCalories();
            proteinByMeal.merge(row.getMealType(), itemProtein, BigDecimal::add);
        }

        int daysLogged() {
            return loggedDates.size();
        }
    }
}
