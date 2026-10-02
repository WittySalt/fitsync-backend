package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
                                         String diet, int page, int size) {
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
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("recipeId")));
        Page<RecipeEntity> recipes = recipeRepository.findAll(specification, pageRequest);
        Map<UUID, RecipeComputation> computations = insightService.compute(recipes.getContent());
        Set<UUID> saved = SCOPE_MINE.equals(normalizedScope) || recipes.isEmpty() ? Set.of()
                : new HashSet<>(favoriteRepository.findSavedRecipeIds(userId,
                        recipes.getContent().stream().map(RecipeEntity::getRecipeId).toList()));
        return recipes.map(recipe -> toListItem(recipe, computations.get(recipe.getRecipeId()),
                saved.contains(recipe.getRecipeId())));
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
        List<RecipeStepView> steps = stepRepository.findByRecipeIdOrderByStepNumberAsc(recipe.getRecipeId()).stream()
                .map(step -> new RecipeStepView(step.getStepNumber(), step.getText()))
                .toList();
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
