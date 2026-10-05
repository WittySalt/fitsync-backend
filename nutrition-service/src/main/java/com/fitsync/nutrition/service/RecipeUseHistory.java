package com.fitsync.nutrition.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.RecentRecipeRow;
import com.fitsync.nutrition.repository.RecipeCookRepository;
import com.fitsync.nutrition.repository.RecipeCookRepository.CookCountRow;
import org.springframework.stereotype.Component;

/**
 * What the caller cooked or logged in the last 30 days, per recipe, for ranking. A cook counts as cooked only
 * when a cook record says so, and the newest activity is the newer of the last cook and the last meal logged
 * from the recipe. Only the caller's own rows are read.
 */
@Component
public class RecipeUseHistory {

    static final int MONTH_DAYS = 30;
    static final int RECENT_DAYS = 7;

    private final RecipeCookRepository cookRepository;
    private final MealNutritionQueryRepository mealQueries;

    public RecipeUseHistory(RecipeCookRepository cookRepository, MealNutritionQueryRepository mealQueries) {
        this.cookRepository = cookRepository;
        this.mealQueries = mealQueries;
    }

    /** cooks counts cook records, lastCook is the newest of them and lastActivity the newest of any trace, both may be null. */
    public record Use(int cooks, Instant lastCook, Instant lastActivity) {

        /** True when a cook or a logged meal fell in the last seven days before now. */
        public boolean recent(Instant now) {
            return lastActivity != null && !lastActivity.isBefore(now.minus(Duration.ofDays(RECENT_DAYS)));
        }
    }

    /** The use of every recipe the caller cooked or logged in the 30 days before now. */
    public Map<UUID, Use> lastMonth(UUID userId, Instant now) {
        Instant since = now.minus(Duration.ofDays(MONTH_DAYS));
        Map<UUID, Use> uses = new HashMap<>();
        for (CookCountRow row : cookRepository.countCooksSince(userId, since)) {
            uses.put(row.getRecipeId(), new Use(row.getTimes().intValue(), row.getLastCookedAt(), row.getLastCookedAt()));
        }
        for (RecentRecipeRow row : mealQueries.findRecipesLoggedSince(userId, since)) {
            uses.merge(row.getRecipeId(), new Use(0, null, row.getLastCookedAt()), RecipeUseHistory::combine);
        }
        return uses;
    }

    private static Use combine(Use first, Use second) {
        Instant latest = first.lastActivity().isAfter(second.lastActivity()) ? first.lastActivity() : second.lastActivity();
        return new Use(first.cooks() + second.cooks(), first.lastCook() != null ? first.lastCook() : second.lastCook(),
                latest);
    }
}
