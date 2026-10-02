package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.NutrientSummaryItemView;
import com.fitsync.nutrition.dto.NutrientSummaryView;
import com.fitsync.nutrition.dto.RecipeDetailIngredientView;
import com.fitsync.nutrition.dto.RecipeDetailView;
import com.fitsync.nutrition.dto.RecipeFitPage;
import com.fitsync.nutrition.dto.RecipeFitView;
import com.fitsync.nutrition.dto.RecipeListItemView;
import com.fitsync.nutrition.dto.RecipeNutrientView;
import com.fitsync.nutrition.dto.RecipeServingNutritionView;
import com.fitsync.nutrition.dto.RecipeStepView;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.entity.RecipeStepEntity;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.RecentRecipeRow;
import com.fitsync.nutrition.repository.RecipeCookRepository;
import com.fitsync.nutrition.repository.RecipeCookRepository.CookedRecipeRow;
import com.fitsync.nutrition.repository.RecipeFavoriteRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.RecipeSearchSpecification;
import com.fitsync.nutrition.repository.RecipeStepRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutrientReferenceService.PersonReferences;
import com.fitsync.nutrition.service.RecipeInsightService.IngredientComputation;
import com.fitsync.nutrition.service.RecipeInsightService.NutrientPerServing;
import com.fitsync.nutrition.service.RecipeInsightService.RecipeComputation;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reading side of recipes: the library, the user's own recipes and the saved ones as pages of cards, one
 * recipe in full, saving a system recipe, and copying a recipe into the caller's own. A recipe is visible
 * when it is a published system recipe or the caller's own, and anything else answers as not found so that
 * ids cannot be probed.
 */
@Service
public class RecipeLibraryService {

    static final String SCOPE_MINE = "mine";
    static final String SCOPE_LIBRARY = "library";
    static final String SCOPE_FAVORITES = "favorites";
    static final String SCOPE_RECENT = "recent";
    static final String SORT_NAME = "name";
    static final String SORT_FIT = "fit";
    static final String SORT_PROTEIN = "protein";
    static final int RECENT_LIMIT = 10;
    // One meal is taken as a third of the daily target when ranking by fit.
    private static final int MEALS_A_DAY = 3;
    // More rows than the limit are read so that recipes that are no longer visible do not shorten the list.
    private static final int RECENT_FETCH = 50;
    private static final String KIND_VITAMIN = "VITAMIN";
    private static final String KIND_MINERAL = "MINERAL";
    static final int MAX_QUERY_LENGTH = 80;
    static final int MAX_CATEGORY_LENGTH = 60;
    static final int MAX_PAGE_SIZE = 50;
    static final int DEFAULT_PAGE_SIZE = 20;
    private static final int TAG_BATCH_SIZE = 200;
    private static final String SOURCE_USER = "USER";
    private static final String NOT_FOUND = "Recipe not found";

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final RecipeFavoriteRepository favoriteRepository;
    private final RecipeInsightService insightService;
    private final NutrientReferenceService referenceService;
    private final PersonLookup personLookup;
    private final Clock clock;
    private final NutritionDiaryService diaryService;
    private final MealNutritionQueryRepository mealQueries;
    private final RecipeCookRepository cookRepository;

    public RecipeLibraryService(RecipeRepository recipeRepository,
                                RecipeIngredientRepository ingredientRepository,
                                RecipeStepRepository stepRepository,
                                RecipeFavoriteRepository favoriteRepository,
                                RecipeInsightService insightService,
                                NutrientReferenceService referenceService,
                                PersonLookup personLookup,
                                Clock clock,
                                NutritionDiaryService diaryService,
                                MealNutritionQueryRepository mealQueries,
                                RecipeCookRepository cookRepository) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.favoriteRepository = favoriteRepository;
        this.insightService = insightService;
        this.referenceService = referenceService;
        this.personLookup = personLookup;
        this.clock = clock;
        this.diaryService = diaryService;
        this.mealQueries = mealQueries;
        this.cookRepository = cookRepository;
    }

    @Transactional(readOnly = true)
    public Page<RecipeListItemView> list(CurrentUserPrincipal principal, String scope, String category, String q,
                                         String diet, String tag, String sort, String timezone, int page, int size) {
        String requiredTag = tagLabel(tag);
        String normalizedScope = normalizeScope(scope);
        String normalizedSort = normalizeSort(sort);
        if (SCOPE_RECENT.equals(normalizedScope)) {
            return recent(principal.userId());
        }
        String cleanCategory = cleanText(category, MAX_CATEGORY_LENGTH, "category");
        String cleanQuery = cleanText(q, MAX_QUERY_LENGTH, "q");
        Set<String> excluded = excludedCategories(diet);
        UUID userId = principal.userId();
        var specification = switch (normalizedScope) {
            case SCOPE_MINE -> RecipeSearchSpecification.mine(userId, cleanCategory, cleanQuery, excluded);
            case SCOPE_LIBRARY -> RecipeSearchSpecification.library(cleanCategory, cleanQuery, excluded);
            case SCOPE_FAVORITES -> RecipeSearchSpecification.favorites(userId, cleanCategory, cleanQuery, excluded);
            default -> throw new BadRequestException("scope must be mine, library, favorites or recent");
        };
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort order = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("recipeId"));
        if (!SORT_NAME.equals(normalizedSort)) {
            return listRanked(principal, normalizedScope, specification, requiredTag, normalizedSort, timezone,
                    pageNumber, pageSize, order);
        }
        if (requiredTag != null) {
            return listWithTag(userId, normalizedScope, specification, requiredTag, pageNumber, pageSize, order);
        }
        Page<RecipeEntity> recipes = recipeRepository.findAll(specification, PageRequest.of(pageNumber, pageSize, order));
        Map<UUID, RecipeComputation> computations = insightService.compute(recipes.getContent());
        Set<UUID> saved = savedIds(userId, normalizedScope, recipes.getContent());
        return recipes.map(recipe -> toListItem(recipe, computations.get(recipe.getRecipeId()),
                saved.contains(recipe.getRecipeId())));
    }

    /**
     * Tags are worked out on read, so the filter walks the candidates in batches, keeps the ones that carry the
     * tag and pages over those. The total is the size of the filtered set.
     */
    private Page<RecipeListItemView> listWithTag(UUID userId, String scope,
                                                 Specification<RecipeEntity> specification,
                                                 String tagLabel, int pageNumber, int pageSize, Sort sort) {
        List<RecipeEntity> matched = new ArrayList<>();
        Map<UUID, RecipeComputation> matchedComputations = new HashMap<>();
        int batch = 0;
        Page<RecipeEntity> candidates;
        do {
            candidates = recipeRepository.findAll(specification, PageRequest.of(batch++, TAG_BATCH_SIZE, sort));
            Map<UUID, RecipeComputation> computations = insightService.compute(candidates.getContent());
            for (RecipeEntity recipe : candidates.getContent()) {
                RecipeComputation computation = computations.get(recipe.getRecipeId());
                if (computation.tags().contains(tagLabel)) {
                    matched.add(recipe);
                    matchedComputations.put(recipe.getRecipeId(), computation);
                }
            }
        } while (candidates.hasNext());
        long from = (long) pageNumber * pageSize;
        List<RecipeEntity> slice = from >= matched.size() ? List.of()
                : matched.subList((int) from, (int) Math.min(matched.size(), from + pageSize));
        Set<UUID> saved = savedIds(userId, scope, slice);
        List<RecipeListItemView> content = slice.stream()
                .map(recipe -> toListItem(recipe, matchedComputations.get(recipe.getRecipeId()),
                        saved.contains(recipe.getRecipeId())))
                .toList();
        return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), matched.size());
    }

    /**
     * Ranks every recipe the filters let through, by how well one serving fits what is left of today or by the
     * protein in one serving, and pages over that order. The recipes are read in batches and scored in memory, which is fine for a library of a
     * few hundred recipes, and nothing that depends on the caller is kept between requests.
     */
    private Page<RecipeListItemView> listRanked(CurrentUserPrincipal principal, String scope,
                                                Specification<RecipeEntity> specification, String tagLabel,
                                                String sortMode, String timezone, int pageNumber, int pageSize,
                                                Sort order) {
        boolean byFit = SORT_FIT.equals(sortMode);
        FitToday today = byFit ? fitToday(principal, timezone) : null;
        List<ScoredRecipe> scored = new ArrayList<>();
        int batch = 0;
        Page<RecipeEntity> candidates;
        do {
            candidates = recipeRepository.findAll(specification, PageRequest.of(batch++, TAG_BATCH_SIZE, order));
            Map<UUID, RecipeComputation> computations = insightService.compute(candidates.getContent());
            for (RecipeEntity recipe : candidates.getContent()) {
                RecipeComputation computation = computations.get(recipe.getRecipeId());
                if (tagLabel != null && !computation.tags().contains(tagLabel)) {
                    continue;
                }
                RecipeFitScorer.Rank rank = null;
                if (byFit) {
                    RecipeFitScorer.Serving serving = new RecipeFitScorer.Serving(computation.kcalPerServing(),
                            computation.proteinPerServing(),
                            nutrientAmount(computation, today.context().nutrientCode()));
                    rank = RecipeFitScorer.rank(today.context(), serving);
                }
                scored.add(new ScoredRecipe(recipe, computation, rank));
            }
        } while (candidates.hasNext());
        Comparator<ScoredRecipe> first = byFit
                ? Comparator.comparing(ScoredRecipe::rank, RecipeFitScorer.Rank.BEST_FIRST)
                : Comparator.comparing((ScoredRecipe item) -> item.computation().proteinPerServing()).reversed();
        scored.sort(first
                .thenComparing(item -> item.recipe().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.recipe().getRecipeId()));
        long from = (long) pageNumber * pageSize;
        List<ScoredRecipe> slice = from >= scored.size() ? List.of()
                : scored.subList((int) from, (int) Math.min(scored.size(), from + pageSize));
        Set<UUID> saved = savedIds(principal.userId(), scope, slice.stream().map(ScoredRecipe::recipe).toList());
        List<RecipeListItemView> content = slice.stream()
                .map(item -> toListItem(item.recipe(), item.computation(), saved.contains(item.recipe().getRecipeId())))
                .toList();
        PageRequest paging = PageRequest.of(pageNumber, pageSize);
        return byFit ? new RecipeFitPage(content, paging, scored.size(), today.view())
                : new PageImpl<>(content, paging, scored.size());
    }

    /** The amount of one nutrient in one serving, or null when the recipe has none of it or no nutrient is asked for. */
    private static BigDecimal nutrientAmount(RecipeComputation computation, String code) {
        if (code == null) {
            return null;
        }
        for (NutrientPerServing nutrient : computation.nutrientsPerServing()) {
            if (code.equals(nutrient.code())) {
                return nutrient.amount();
            }
        }
        return null;
    }

    /**
     * Today's calories and protein left and the lowest nutrient, from the same summaries the diary shows. The
     * calories left can be negative, and the protein short is never below zero. The lowest nutrient is the
     * vitamin or mineral with the smallest share of its daily reference among those the day's food has any
     * figure for, and it is left out until something is logged today.
     */
    private FitToday fitToday(CurrentUserPrincipal principal, String timezone) {
        ZoneId zone = NutritionPageInsightServiceImpl.resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        DailyNutritionSummaryView day = diaryService.getDailySummary(principal, today, zone.getId());
        Integer kcalLeft = day.remainingCalories();
        BigDecimal proteinShort = day.remainingProteinGrams() == null ? null
                : day.remainingProteinGrams().max(BigDecimal.ZERO);
        NutrientSummaryItemView lowest = null;
        NutrientSummaryView nutrients = diaryService.getNutrientSummary(principal, today, today, zone.getId());
        if (nutrients.daysLogged() > 0) {
            for (NutrientSummaryItemView item : nutrients.nutrients()) {
                boolean vitaminOrMineral = KIND_VITAMIN.equals(item.kind()) || KIND_MINERAL.equals(item.kind());
                boolean usable = vitaminOrMineral && item.share() != null
                        && item.reference() != null && item.reference().signum() > 0;
                if (usable && (lowest == null || item.share().compareTo(lowest.share()) < 0)) {
                    lowest = item;
                }
            }
        }
        RecipeFitView view = new RecipeFitView(kcalLeft, proteinShort, lowest == null ? null
                : new RecipeFitView.LowestNutrient(lowest.code(), lowest.displayName()));
        Integer mealKcal = day.targetCalories() == null || day.targetCalories() <= 0 ? null
                : Math.round(day.targetCalories() / (float) MEALS_A_DAY);
        RecipeFitScorer.Context context = new RecipeFitScorer.Context(kcalLeft, proteinShort,
                lowest == null ? null : lowest.code(), lowest == null ? null : lowest.reference(), mealKcal);
        return new FitToday(context, view);
    }

    private record FitToday(RecipeFitScorer.Context context, RecipeFitView view) {
    }

    private record ScoredRecipe(RecipeEntity recipe, RecipeComputation computation, RecipeFitScorer.Rank rank) {
    }

    /**
     * The recipes the caller most recently logged a meal from, newest first, at most ten, each with the time of
     * that meal. Only the caller's own meals are read, meals marked as deleted do not count, and a recipe that
     * the caller can no longer see is left out.
     */
    private Page<RecipeListItemView> recent(UUID userId) {
        // The recorded cooks and the meals that came from a recipe (the only trace before cooks were recorded)
        // are merged, and a recipe shows the newest time of the two.
        Map<UUID, Instant> newest = new HashMap<>();
        for (RecentRecipeRow row : mealQueries.findRecentRecipes(userId, PageRequest.of(0, RECENT_FETCH))) {
            newest.merge(row.getRecipeId(), row.getLastCookedAt(), RecipeLibraryService::later);
        }
        for (CookedRecipeRow row : cookRepository.findRecentRecipes(userId, PageRequest.of(0, RECENT_FETCH))) {
            newest.merge(row.getRecipeId(), row.getLastCookedAt(), RecipeLibraryService::later);
        }
        List<UUID> ordered = newest.entrySet().stream()
                .sorted(Map.Entry.<UUID, Instant>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        Map<UUID, RecipeEntity> byId = new HashMap<>();
        recipeRepository.findAllById(ordered).forEach(recipe -> byId.put(recipe.getRecipeId(), recipe));
        Map<UUID, Instant> lastCooked = new LinkedHashMap<>();
        for (UUID recipeId : ordered) {
            RecipeEntity recipe = byId.get(recipeId);
            if (recipe != null && isVisible(userId, recipe) && lastCooked.size() < RECENT_LIMIT) {
                lastCooked.put(recipeId, newest.get(recipeId));
            }
        }
        List<RecipeEntity> recipes = lastCooked.keySet().stream().map(byId::get).toList();
        Map<UUID, RecipeComputation> computations = insightService.compute(recipes);
        Set<UUID> saved = savedIds(userId, SCOPE_RECENT, recipes);
        List<RecipeListItemView> content = recipes.stream()
                .map(recipe -> toListItem(recipe, computations.get(recipe.getRecipeId()),
                        saved.contains(recipe.getRecipeId()), lastCooked.get(recipe.getRecipeId())))
                .toList();
        return new PageImpl<>(content, PageRequest.of(0, RECENT_LIMIT), content.size());
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private Set<UUID> savedIds(UUID userId, String scope, List<RecipeEntity> recipes) {
        if (SCOPE_MINE.equals(scope) || recipes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(favoriteRepository.findSavedRecipeIds(userId,
                recipes.stream().map(RecipeEntity::getRecipeId).toList()));
    }

    private static String tagLabel(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return switch (tag.trim()) {
            case "HIGH_PROTEIN" -> RecipeInsightService.TAG_HIGH_PROTEIN;
            case "HIGH_FIBRE" -> RecipeInsightService.TAG_HIGH_FIBRE;
            case "HIGH_SUGAR" -> RecipeInsightService.TAG_HIGH_SUGAR;
            case "HIGH_SALT" -> RecipeInsightService.TAG_HIGH_SALT;
            default -> throw new BadRequestException("tag must be HIGH_PROTEIN, HIGH_FIBRE, HIGH_SUGAR or HIGH_SALT");
        };
    }

    @Transactional(readOnly = true)
    public RecipeDetailView detail(CurrentUserPrincipal principal, UUID recipeId) {
        return toDetail(principal.userId(), findVisible(principal.userId(), recipeId));
    }

    /** The recipe when the caller may see it: a published system recipe or one of their own. */
    @Transactional(readOnly = true)
    public RecipeEntity findVisible(UUID userId, UUID recipeId) {
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!isVisible(userId, recipe)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return recipe;
    }

    /** True for a recipe that is not deleted and is either a published system recipe or one of the caller's own. */
    private static boolean isVisible(UUID userId, RecipeEntity recipe) {
        if (recipe.getDeletedAt() != null) {
            return false;
        }
        boolean shared = recipe.getScope() == RecipeScope.SYSTEM && recipe.isPublished();
        boolean own = recipe.getScope() == RecipeScope.USER && userId.equals(recipe.getUserId());
        return shared || own;
    }

    @Transactional
    public void favorite(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity recipe = findSharedRecipe(recipeId);
        if (favoriteRepository.findByUserIdAndRecipeId(principal.userId(), recipe.getRecipeId()).isPresent()) {
            return;
        }
        RecipeFavoriteEntity favorite = new RecipeFavoriteEntity();
        favorite.setFavoriteId(UUID.randomUUID());
        favorite.setUserId(principal.userId());
        favorite.setRecipeId(recipe.getRecipeId());
        favorite.setSavedAt(Instant.now(Clock.systemUTC()));
        favoriteRepository.save(favorite);
    }

    @Transactional
    public void unfavorite(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity recipe = findSharedRecipe(recipeId);
        favoriteRepository.findByUserIdAndRecipeId(principal.userId(), recipe.getRecipeId())
                .ifPresent(favoriteRepository::delete);
    }

    @Transactional
    public RecipeDetailView copy(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity source = findVisible(principal.userId(), recipeId);
        RecipeEntity copy = new RecipeEntity();
        copy.setRecipeId(UUID.randomUUID());
        copy.setUserId(principal.userId());
        copy.setScope(RecipeScope.USER);
        copy.setName(source.getName());
        copy.setServings(source.getServings());
        copy.setNote(source.getNote());
        // The origin stays on the copy so that the credit and the link to the original recipe survive.
        copy.setSource(source.getSource() == null ? SOURCE_USER : source.getSource());
        copy.setSourceRef(source.getSourceRef());
        copy.setSourceUrl(source.getSourceUrl());
        copy.setImageUrl(source.getImageUrl());
        copy.setImageCredit(source.getImageCredit());
        copy.setCategory(source.getCategory());
        copy.setCuisine(source.getCuisine());
        copy.setPrepMinutes(source.getPrepMinutes());
        copy.setPublished(true);
        RecipeEntity saved = recipeRepository.save(copy);
        for (RecipeIngredientEntity original : ingredientRepository.findByRecipeIdOrderBySortOrderAsc(source.getRecipeId())) {
            RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
            ingredient.setIngredientId(UUID.randomUUID());
            ingredient.setRecipeId(saved.getRecipeId());
            ingredient.setFoodCatalogItemId(original.getFoodCatalogItemId());
            ingredient.setFoodServingId(original.getFoodServingId());
            ingredient.setQuantity(original.getQuantity());
            ingredient.setSortOrder(original.getSortOrder());
            ingredient.setDisplayText(original.getDisplayText());
            ingredient.setGrams(original.getGrams());
            ingredientRepository.save(ingredient);
        }
        List<String> steps = stepRepository.findByRecipeIdOrderByStepNumberAsc(source.getRecipeId()).stream()
                .map(RecipeStepEntity::getText)
                .toList();
        replaceSteps(saved.getRecipeId(), steps);
        return toDetail(principal.userId(), saved);
    }

    /** Replaces the steps of a recipe; the step numbers follow the order of the texts, from 1. */
    @Transactional
    public void replaceSteps(UUID recipeId, List<String> texts) {
        stepRepository.deleteByRecipeId(recipeId);
        // The unique step number needs the old rows gone before the new ones are written.
        stepRepository.flush();
        int number = 1;
        for (String text : texts) {
            RecipeStepEntity step = new RecipeStepEntity();
            step.setStepId(UUID.randomUUID());
            step.setRecipeId(recipeId);
            step.setStepNumber(number++);
            step.setText(text.trim());
            stepRepository.save(step);
        }
    }

    private RecipeEntity findSharedRecipe(UUID recipeId) {
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (recipe.getDeletedAt() != null || recipe.getScope() != RecipeScope.SYSTEM || !recipe.isPublished()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return recipe;
    }

    private RecipeListItemView toListItem(RecipeEntity recipe, RecipeComputation computation, boolean favorite) {
        return toListItem(recipe, computation, favorite, null);
    }

    private RecipeListItemView toListItem(RecipeEntity recipe, RecipeComputation computation, boolean favorite,
                                          Instant lastCookedAt) {
        return new RecipeListItemView(
                recipe.getRecipeId(),
                recipe.getName(),
                recipe.getScope().name(),
                recipe.getImageUrl(),
                recipe.getImageCredit(),
                recipe.getCategory(),
                recipe.getCuisine(),
                recipe.getServings(),
                computation.kcalPerServing(),
                computation.proteinPerServing(),
                computation.fatPerServing(),
                computation.tags(),
                computation.dietFlags(),
                computation.topFoodGroups(),
                favorite,
                lastCookedAt);
    }

    /** Imported recipes show the ingredient word of the written line; the user's own show the food's name. */
    private static String shortName(RecipeEntity recipe, IngredientComputation item) {
        if (!SOURCE_USER.equals(recipe.getSource()) && item.displayText() != null) {
            String parsed = RecipeStepAnalyzer.ingredientName(item.displayText());
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return item.foodName();
    }

    private RecipeDetailView toDetail(UUID userId, RecipeEntity recipe) {
        RecipeComputation computation = insightService.compute(recipe);
        PersonReferences references = referenceService.forPerson(personLookup.facts(userId).basics());
        List<RecipeNutrientView> nutrients = new ArrayList<>();
        for (NutrientPerServing nutrient : computation.nutrientsPerServing()) {
            nutrients.add(new RecipeNutrientView(nutrient.code(), nutrient.displayName(), nutrient.amount(),
                    nutrient.unit(), shareOf(nutrient.amount(), references.target(nutrient.code()))));
        }
        List<RecipeDetailIngredientView> ingredients = new ArrayList<>();
        for (IngredientComputation item : computation.ingredients()) {
            String text = item.displayText() == null || item.displayText().isBlank()
                    ? item.foodName() : item.displayText();
            ingredients.add(new RecipeDetailIngredientView(item.ingredientId(), text,
                    item.foodCatalogItemId(), item.foodName(), shortName(recipe, item),
                    item.grams() == null ? null : item.grams().setScale(2, RoundingMode.HALF_UP),
                    item.foodGroup(), item.categoryCode()));
        }
        List<RecipeStepEntity> stepRows = stepRepository.findByRecipeIdOrderByStepNumberAsc(recipe.getRecipeId());
        List<List<UUID>> usedIngredients = RecipeStepAnalyzer.ingredientIdsByStep(
                stepRows.stream().map(RecipeStepEntity::getText).toList(),
                computation.ingredients().stream()
                        .map(item -> new RecipeStepAnalyzer.IngredientRef(item.ingredientId(), item.foodName(),
                                item.displayText()))
                        .toList());
        Map<UUID, String> shortNames = new HashMap<>();
        for (RecipeDetailIngredientView ingredient : ingredients) {
            shortNames.put(ingredient.ingredientId(), ingredient.name());
        }
        List<RecipeStepView> steps = new ArrayList<>();
        for (int i = 0; i < stepRows.size(); i++) {
            RecipeStepEntity step = stepRows.get(i);
            String mainIngredient = usedIngredients.get(i).isEmpty()
                    ? null : shortNames.get(usedIngredients.get(i).get(0));
            steps.add(new RecipeStepView(step.getStepNumber(), step.getText(),
                    RecipeStepAnalyzer.title(step.getText(), step.getStepNumber()),
                    usedIngredients.get(i), RecipeStepAnalyzer.timers(step.getText(), step.getStepNumber(), mainIngredient)));
        }
        boolean shared = recipe.getScope() == RecipeScope.SYSTEM;
        boolean favorite = shared && favoriteRepository.findByUserIdAndRecipeId(userId, recipe.getRecipeId()).isPresent();
        boolean editable = recipe.getScope() == RecipeScope.USER && userId.equals(recipe.getUserId());
        RecipeServingNutritionView perServing = new RecipeServingNutritionView(
                computation.kcalPerServing(),
                computation.proteinPerServing(),
                computation.carbsPerServing(),
                computation.fatPerServing(),
                computation.fibrePerServing(),
                computation.sugarPerServing(),
                computation.sodiumPerServing(),
                computation.saturatedFatPerServing(),
                computation.potassiumPerServing(),
                nutrients);
        return new RecipeDetailView(
                recipe.getRecipeId(),
                recipe.getName(),
                recipe.getScope().name(),
                recipe.getServings(),
                recipe.getNote(),
                recipe.getCategory(),
                recipe.getCuisine(),
                recipe.getPrepMinutes(),
                recipe.getSource(),
                recipe.getSourceUrl(),
                recipe.getImageUrl(),
                recipe.getImageCredit(),
                ingredients,
                steps,
                perServing,
                computation.groupsGrams(),
                computation.tags(),
                computation.dietFlags(),
                favorite,
                editable,
                cookRepository.countByUserIdAndRecipeIdAndDeletedAtIsNull(userId, recipe.getRecipeId()),
                cookRepository.findLastFinishedAt(userId, recipe.getRecipeId()).orElse(null));
    }

    private static BigDecimal shareOf(BigDecimal amount, BigDecimal reference) {
        if (reference == null || reference.signum() <= 0) {
            return null;
        }
        return amount.divide(reference, 3, RoundingMode.HALF_UP);
    }

    private static String cleanText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException(name + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static Set<String> excludedCategories(String diet) {
        if (diet == null || diet.isBlank()) {
            return Set.of();
        }
        String wanted = diet.trim();
        if (RecipeInsightService.DIET_VEGETARIAN.equalsIgnoreCase(wanted)) {
            return RecipeInsightService.NON_VEGETARIAN_CATEGORIES;
        }
        if (RecipeInsightService.DIET_VEGAN.equalsIgnoreCase(wanted)) {
            return RecipeInsightService.NON_VEGAN_CATEGORIES;
        }
        throw new BadRequestException("diet must be VEGETARIAN or VEGAN");
    }

    /** Maps the scope text to one of the known scopes, or an empty text when it is none of them. */
    private static String normalizeScope(String scope) {
        String wanted = scope == null ? "" : scope.trim();
        for (String known : List.of(SCOPE_MINE, SCOPE_LIBRARY, SCOPE_FAVORITES, SCOPE_RECENT)) {
            if (known.equalsIgnoreCase(wanted)) {
                return known;
            }
        }
        return "";
    }

    /** The sort is the name order, which is the default, the fit for today, or the protein per serving. */
    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank() || SORT_NAME.equalsIgnoreCase(sort.trim())) {
            return SORT_NAME;
        }
        if (SORT_FIT.equalsIgnoreCase(sort.trim())) {
            return SORT_FIT;
        }
        if (SORT_PROTEIN.equalsIgnoreCase(sort.trim())) {
            return SORT_PROTEIN;
        }
        throw new BadRequestException("sort must be name, fit or protein");
    }
}
