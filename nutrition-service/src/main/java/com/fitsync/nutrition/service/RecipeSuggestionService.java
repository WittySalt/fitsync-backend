package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.RecipeSuggestionsView;
import com.fitsync.nutrition.dto.RecipeSuggestionsView.Facts;
import com.fitsync.nutrition.dto.RecipeSuggestionsView.State;
import com.fitsync.nutrition.dto.RecipeSuggestionsView.Suggestion;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealNutritionRow;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.RecipeSearchSpecification;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.RecipeInsightService.RecipeComputation;
import com.fitsync.nutrition.service.RecipeLibraryService.FitToday;
import com.fitsync.nutrition.service.RecipeSuggestionRanking.Rank;
import com.fitsync.web.error.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What to cook tonight. Today is read the way the Diary reads it, one state is chosen from it, and the
 * suggestions are the best recipes for that state by {@link RecipeSuggestionRanking}, the same ranking the
 * Library uses for "Fits today". Only the caller's own meals and cooks are read.
 */
@Service
public class RecipeSuggestionService {

    static final int MAX_SUGGESTIONS = 3;
    private static final int PROTEIN_ONLY_COUNT = 2;
    private static final int MEALS_A_DAY = 3;
    private static final int DINNER_WINDOW_DAYS = 30;
    private static final int MIN_DINNERS = 3;
    private static final int LIGHT_KCAL_BELOW = 400;
    private static final int LIGHT_MIN_PROTEIN_GRAMS = 15;
    private static final int PROTEIN_ONLY_MIN_PROTEIN_GRAMS = 25;
    private static final double MEAL_BAND = 0.15;
    private static final String FOOD_GROUP_VEGETABLES = "VEG";
    private static final String REASON_MOST_COOKED = "Your most cooked this month";
    private static final int PAGE_SIZE = 200;

    private final RecipeLibraryService libraryService;
    private final RecipeUseHistory useHistory;
    private final RecipeRepository recipeRepository;
    private final RecipeInsightService insightService;
    private final MealNutritionQueryRepository mealQueries;
    private final Clock clock;

    public RecipeSuggestionService(RecipeLibraryService libraryService, RecipeUseHistory useHistory,
                                   RecipeRepository recipeRepository, RecipeInsightService insightService,
                                   MealNutritionQueryRepository mealQueries, Clock clock) {
        this.libraryService = libraryService;
        this.useHistory = useHistory;
        this.recipeRepository = recipeRepository;
        this.insightService = insightService;
        this.mealQueries = mealQueries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecipeSuggestionsView suggestions(CurrentUserPrincipal principal, int limit, String timezone) {
        if (limit < 1 || limit > MAX_SUGGESTIONS) {
            throw new BadRequestException("limit must be between 1 and " + MAX_SUGGESTIONS);
        }
        ZoneId zone = NutritionPageInsightServiceImpl.resolveZone(timezone);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        UUID userId = principal.userId();
        FitToday fit = libraryService.fitToday(principal, timezone);
        DailyNutritionSummaryView day = fit.day();

        List<MealNutritionRow> rows = mealQueries.findMealNutritionRows(userId,
                today.minusDays(DINNER_WINDOW_DAYS).atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        boolean dinnerToday = false;
        boolean anyMealToday = false;
        Set<Instant> dinners = new HashSet<>();
        for (MealNutritionRow row : rows) {
            boolean isToday = !row.getConsumedAt().isBefore(startOfToday);
            if (isToday && row.getMealType() != MealType.FAST) {
                anyMealToday = true;
                dinnerToday |= row.getMealType() == MealType.DINNER;
            }
            if (row.getMealType() == MealType.DINNER) {
                dinners.add(row.getConsumedAt());
            }
        }
        String usualDinner = usualDinnerTime(dinners, zone);

        Facts facts = new Facts(fit.view().kcalLeft(), fit.view().proteinShortGrams(), fit.view().lowestNutrient(),
                usualDinner, day.totalCalories(), day.targetCalories(),
                day.remainingProteinGrams() == null ? null : day.remainingProteinGrams().signum() <= 0);

        State state = stateOf(dinnerToday, anyMealToday, day, fit);
        if (state == State.DINNER_LOGGED) {
            return new RecipeSuggestionsView(state, facts, List.of());
        }
        int count = switch (state) {
            case FITS -> limit;
            case PROTEIN_ONLY -> Math.min(limit, PROTEIN_ONLY_COUNT);
            default -> 1;
        };
        List<Candidate> candidates = candidates(userId);
        Map<UUID, RecipeUseHistory.Use> uses = useHistory.lastMonth(userId, now);
        List<Suggestion> picked = switch (state) {
            case NOTHING_LOGGED -> nothingLogged(candidates, uses, fit, now);
            case PAST_TARGET -> pastTarget(candidates, uses);
            case PROTEIN_ONLY -> ranked(candidates, uses, fit, now, count, PROTEIN_ONLY_MIN_PROTEIN_GRAMS);
            default -> ranked(candidates, uses, fit, now, count, 0);
        };
        return new RecipeSuggestionsView(state, facts, picked.stream().limit(count).toList());
    }

    private static State stateOf(boolean dinnerToday, boolean anyMealToday, DailyNutritionSummaryView day,
                                 FitToday fit) {
        if (dinnerToday) {
            return State.DINNER_LOGGED;
        }
        if (!anyMealToday) {
            return State.NOTHING_LOGGED;
        }
        Integer target = day.targetCalories();
        if (target != null && target > 0 && day.totalCalories() >= target) {
            return State.PAST_TARGET;
        }
        Integer left = fit.view().kcalLeft();
        BigDecimal proteinShort = fit.view().proteinShortGrams();
        if (target != null && target > 0 && left != null && proteinShort != null && proteinShort.signum() > 0) {
            double meal = target / (double) MEALS_A_DAY;
            if (Math.abs(left - meal) <= MEAL_BAND * meal) {
                return State.PROTEIN_ONLY;
            }
        }
        return State.FITS;
    }

    /** The most cooked recipe of the last 30 days, or the best Library pick when none was ever cooked. */
    private List<Suggestion> nothingLogged(List<Candidate> candidates, Map<UUID, RecipeUseHistory.Use> uses,
                                           FitToday fit, Instant now) {
        Candidate favourite = null;
        for (Candidate candidate : candidates) {
            RecipeUseHistory.Use use = uses.get(candidate.recipe().getRecipeId());
            if (use == null || use.cooks() == 0) {
                continue;
            }
            if (favourite == null || compareMostCooked(use, uses.get(favourite.recipe().getRecipeId()),
                    candidate, favourite) < 0) {
                favourite = candidate;
            }
        }
        if (favourite != null) {
            RecipeUseHistory.Use use = uses.get(favourite.recipe().getRecipeId());
            return List.of(toSuggestion(favourite, REASON_MOST_COOKED, sourceOf(favourite, use)));
        }
        // Nothing eaten yet, so the calories left would be the whole day. Only the size of one meal is used.
        RecipeSuggestionRanking.Context context = new RecipeSuggestionRanking.Context(null,
                fit.context().proteinShortGrams(), null, null, null, fit.context().mealKcal());
        return candidates.stream()
                .filter(candidate -> candidate.recipe().getScope() == RecipeScope.SYSTEM)
                .map(candidate -> scored(candidate, context, null, now))
                .sorted(Comparator.comparing(Scored::rank, Rank.BEST_FIRST).thenComparing(Scored::name,
                        String.CASE_INSENSITIVE_ORDER).thenComparing(Scored::id))
                .limit(1)
                .map(item -> toSuggestion(item.candidate(), RecipeSuggestionRanking.reason(context, item.serving()),
                        RecipeSuggestionsView.Source.LIBRARY))
                .toList();
    }

    /** More cooks first, then the newer cook, then the name. */
    private static int compareMostCooked(RecipeUseHistory.Use use, RecipeUseHistory.Use other, Candidate candidate,
                                         Candidate otherCandidate) {
        int byCooks = Integer.compare(other.cooks(), use.cooks());
        if (byCooks != 0) {
            return byCooks;
        }
        int byNewest = other.lastCook().compareTo(use.lastCook());
        if (byNewest != 0) {
            return byNewest;
        }
        return candidate.recipe().getName().compareToIgnoreCase(otherCandidate.recipe().getName());
    }

    /** The lightest recipe under 400 kcal a serving with at least 15 g of protein. */
    private List<Suggestion> pastTarget(List<Candidate> candidates, Map<UUID, RecipeUseHistory.Use> uses) {
        return candidates.stream()
                .filter(candidate -> candidate.computation().kcalPerServing() < LIGHT_KCAL_BELOW
                        && candidate.computation().proteinPerServing()
                        .compareTo(BigDecimal.valueOf(LIGHT_MIN_PROTEIN_GRAMS)) >= 0)
                .min(Comparator.comparingInt((Candidate candidate) -> candidate.computation().kcalPerServing())
                        .thenComparing(candidate -> candidate.recipe().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(candidate -> candidate.recipe().getRecipeId()))
                .map(candidate -> {
                    int kcal = candidate.computation().kcalPerServing();
                    String reason = mostlyVegetables(candidate.computation())
                            ? kcal + " kcal, mostly vegetables" : kcal + " kcal, a light option";
                    return toSuggestion(candidate, reason,
                            sourceOf(candidate, uses.get(candidate.recipe().getRecipeId())));
                })
                .stream()
                .toList();
    }

    /** True when the food group with the most grams in the recipe is the vegetables. */
    static boolean mostlyVegetables(RecipeComputation computation) {
        return computation.groupsGrams().keySet().stream().findFirst()
                .map(FOOD_GROUP_VEGETABLES::equals).orElse(false);
    }

    private List<Suggestion> ranked(List<Candidate> candidates, Map<UUID, RecipeUseHistory.Use> uses, FitToday fit,
                                    Instant now, int count, int minProteinGrams) {
        BigDecimal minProtein = BigDecimal.valueOf(minProteinGrams);
        return candidates.stream()
                .filter(candidate -> candidate.computation().proteinPerServing().compareTo(minProtein) >= 0)
                .map(candidate -> scored(candidate, fit.context(), uses.get(candidate.recipe().getRecipeId()), now))
                .sorted(Comparator.comparing(Scored::rank, Rank.BEST_FIRST)
                        .thenComparing(Scored::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Scored::id))
                .limit(count)
                .map(item -> toSuggestion(item.candidate(), RecipeSuggestionRanking.reason(fit.context(),
                        item.serving()), item.source()))
                .toList();
    }

    private static Scored scored(Candidate candidate, RecipeSuggestionRanking.Context context,
                                 RecipeUseHistory.Use use, Instant now) {
        RecipeSuggestionRanking.Serving serving = RecipeLibraryService.servingOf(candidate.recipe(),
                candidate.computation(), context, use, now);
        return new Scored(candidate, serving, RecipeSuggestionRanking.rank(context, serving));
    }

    private static RecipeSuggestionsView.Source sourceOf(Candidate candidate, RecipeUseHistory.Use use) {
        return toDtoSource(RecipeLibraryService.servingOf(candidate.recipe(), candidate.computation(),
                new RecipeSuggestionRanking.Context(null, null, null, null), use, Instant.EPOCH).source());
    }

    private static RecipeSuggestionsView.Source toDtoSource(RecipeSuggestionRanking.Source source) {
        return RecipeSuggestionsView.Source.valueOf(source.name());
    }

    private static Suggestion toSuggestion(Candidate candidate, String reason, RecipeSuggestionsView.Source source) {
        RecipeEntity recipe = candidate.recipe();
        return new Suggestion(recipe.getRecipeId(), recipe.getName(), recipe.getImageUrl(),
                candidate.computation().kcalPerServing(),
                candidate.computation().proteinPerServing().setScale(1, RoundingMode.HALF_UP), reason, source);
    }

    /** Every recipe the caller can see, their own and the published Library, with its figures per serving. */
    private List<Candidate> candidates(UUID userId) {
        List<Candidate> all = new ArrayList<>();
        Sort order = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("recipeId"));
        for (var specification : List.of(RecipeSearchSpecification.mine(userId, null, null, Set.of()),
                RecipeSearchSpecification.library(null, null, Set.of()))) {
            int page = 0;
            Page<RecipeEntity> batch;
            do {
                batch = recipeRepository.findAll(specification, PageRequest.of(page++, PAGE_SIZE, order));
                Map<UUID, RecipeComputation> computations = insightService.compute(batch.getContent());
                for (RecipeEntity recipe : batch.getContent()) {
                    all.add(new Candidate(recipe, computations.get(recipe.getRecipeId())));
                }
            } while (batch.hasNext());
        }
        return all;
    }

    /** The median dinner time of the last 30 days as HH:mm, or null when there were fewer than three dinners. */
    static String usualDinnerTime(Set<Instant> dinners, ZoneId zone) {
        if (dinners.size() < MIN_DINNERS) {
            return null;
        }
        List<Integer> seconds = dinners.stream()
                .map(time -> LocalTime.ofInstant(time, zone).toSecondOfDay())
                .sorted()
                .toList();
        int middle = seconds.size() / 2;
        int median = seconds.size() % 2 == 1 ? seconds.get(middle)
                : (seconds.get(middle - 1) + seconds.get(middle)) / 2;
        LocalTime time = LocalTime.ofSecondOfDay(median);
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private record Candidate(RecipeEntity recipe, RecipeComputation computation) {
    }

    private record Scored(Candidate candidate, RecipeSuggestionRanking.Serving serving, Rank rank) {

        String name() {
            return candidate.recipe().getName();
        }

        UUID id() {
            return candidate.recipe().getRecipeId();
        }

        RecipeSuggestionsView.Source source() {
            return toDtoSource(serving.source());
        }
    }
}
