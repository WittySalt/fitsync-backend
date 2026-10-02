package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.dto.RecipeDetailIngredientView;
import com.fitsync.nutrition.dto.RecipeDetailView;
import com.fitsync.nutrition.dto.RecipeListItemView;
import com.fitsync.nutrition.dto.RecipeNutrientView;
import com.fitsync.nutrition.dto.RecipeServingNutritionView;
import com.fitsync.nutrition.dto.RecipeStepView;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.entity.RecipeStepEntity;
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

    public RecipeLibraryService(RecipeRepository recipeRepository,
                                RecipeIngredientRepository ingredientRepository,
                                RecipeStepRepository stepRepository,
                                RecipeFavoriteRepository favoriteRepository,
                                RecipeInsightService insightService,
                                NutrientReferenceService referenceService,
                                PersonLookup personLookup) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.favoriteRepository = favoriteRepository;
        this.insightService = insightService;
        this.referenceService = referenceService;
        this.personLookup = personLookup;
    }

    @Transactional(readOnly = true)
    public Page<RecipeListItemView> list(CurrentUserPrincipal principal, String scope, String category, String q,
                                         String diet, String tag, int page, int size) {
        String requiredTag = tagLabel(tag);
        String normalizedScope = normalizeScope(scope);
        String cleanCategory = cleanText(category, MAX_CATEGORY_LENGTH, "category");
        String cleanQuery = cleanText(q, MAX_QUERY_LENGTH, "q");
        Set<String> excluded = excludedCategories(diet);
        UUID userId = principal.userId();
        var specification = switch (normalizedScope) {
            case SCOPE_MINE -> RecipeSearchSpecification.mine(userId, cleanCategory, cleanQuery, excluded);
            case SCOPE_LIBRARY -> RecipeSearchSpecification.library(cleanCategory, cleanQuery, excluded);
            case SCOPE_FAVORITES -> RecipeSearchSpecification.favorites(userId, cleanCategory, cleanQuery, excluded);
            default -> throw new BadRequestException("scope must be mine, library or favorites");
        };
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("recipeId"));
        if (requiredTag != null) {
            return listWithTag(userId, normalizedScope, specification, requiredTag, pageNumber, pageSize, sort);
        }
        Page<RecipeEntity> recipes = recipeRepository.findAll(specification, PageRequest.of(pageNumber, pageSize, sort));
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
        if (recipe.getDeletedAt() != null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        boolean shared = recipe.getScope() == RecipeScope.SYSTEM && recipe.isPublished();
        boolean own = recipe.getScope() == RecipeScope.USER && userId.equals(recipe.getUserId());
        if (!shared && !own) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return recipe;
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
                computation.tags(),
                computation.dietFlags(),
                computation.topFoodGroups(),
                favorite);
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
                    item.foodCatalogItemId(), item.foodName(),
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
        List<RecipeStepView> steps = new ArrayList<>();
        for (int i = 0; i < stepRows.size(); i++) {
            RecipeStepEntity step = stepRows.get(i);
            steps.add(new RecipeStepView(step.getStepNumber(), step.getText(),
                    RecipeStepAnalyzer.title(step.getText(), step.getStepNumber()),
                    usedIngredients.get(i), RecipeStepAnalyzer.timers(step.getText())));
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
                editable);
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
        for (String known : List.of(SCOPE_MINE, SCOPE_LIBRARY, SCOPE_FAVORITES)) {
            if (known.equalsIgnoreCase(wanted)) {
                return known;
            }
        }
        return "";
    }
}
