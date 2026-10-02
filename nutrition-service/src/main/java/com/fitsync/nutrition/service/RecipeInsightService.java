package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out what a recipe contains from its ingredients: the nutrition of one serving, the grams of every
 * food group, and the tags and diet flags that follow from them. Nothing is stored, so a change in the food
 * catalogue shows up in every recipe. A page of recipes is computed with one query per table and not one
 * per recipe.
 */
@Service
public class RecipeInsightService {

    public static final String TAG_HIGH_PROTEIN = "High protein";
    public static final String TAG_HIGH_FIBRE = "High fibre";
    public static final String TAG_HIGH_SUGAR = "High sugar";
    public static final String TAG_HIGH_SALT = "High salt";
    public static final String DIET_VEGETARIAN = "VEGETARIAN";
    public static final String DIET_VEGAN = "VEGAN";

    /** The food categories that rule a recipe out of a vegetarian diet. */
    public static final Set<String> NON_VEGETARIAN_CATEGORIES = Set.of("MEAT_POULTRY", "SEAFOOD");
    /** The food categories that rule a recipe out of a vegan diet, on top of the vegetarian ones. */
    public static final Set<String> NON_VEGAN_CATEGORIES = Set.of("MEAT_POULTRY", "SEAFOOD", "DAIRY", "EGGS");

    private static final String FOOD_GROUP_OTHER = "OTHER";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal PROTEIN_ENERGY_SHARE = new BigDecimal("0.20");
    private static final BigDecimal KCAL_PER_GRAM_PROTEIN = BigDecimal.valueOf(4);
    private static final BigDecimal HIGH_PROTEIN_MIN_PER_100G = BigDecimal.valueOf(3);
    private static final BigDecimal HIGH_FIBRE_MIN_PER_100G = BigDecimal.valueOf(6);
    private static final BigDecimal HIGH_SUGAR_OVER_PER_100G = new BigDecimal("22.5");
    private static final BigDecimal HIGH_SALT_OVER_MG_PER_100G = BigDecimal.valueOf(600);
    private static final String POTASSIUM = "POTASSIUM";
    private static final int TOP_GROUPS = 3;

    private final RecipeIngredientRepository ingredientRepository;
    private final FoodCatalogItemRepository foodRepository;
    private final FoodNutrientProfileRepository profileRepository;
    private final FoodNutrientDetailRepository detailRepository;
    private final FoodServingRepository servingRepository;
    private final FoodCategoryRepository categoryRepository;

    public RecipeInsightService(RecipeIngredientRepository ingredientRepository,
                                FoodCatalogItemRepository foodRepository,
                                FoodNutrientProfileRepository profileRepository,
                                FoodNutrientDetailRepository detailRepository,
                                FoodServingRepository servingRepository,
                                FoodCategoryRepository categoryRepository) {
        this.ingredientRepository = ingredientRepository;
        this.foodRepository = foodRepository;
        this.profileRepository = profileRepository;
        this.detailRepository = detailRepository;
        this.servingRepository = servingRepository;
        this.categoryRepository = categoryRepository;
    }

    /** The computation for every given recipe, keyed by recipe id. */
    @Transactional(readOnly = true)
    public Map<UUID, RecipeComputation> compute(Collection<RecipeEntity> recipes) {
        if (recipes.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<UUID> recipeIds = recipes.stream().map(RecipeEntity::getRecipeId).toList();
        Map<UUID, List<RecipeIngredientEntity>> ingredientsByRecipe = new HashMap<>();
        for (RecipeIngredientEntity ingredient : ingredientRepository.findByRecipeIdInOrderByRecipeIdAscSortOrderAsc(recipeIds)) {
            ingredientsByRecipe.computeIfAbsent(ingredient.getRecipeId(), id -> new ArrayList<>()).add(ingredient);
        }
        List<UUID> foodIds = ingredientsByRecipe.values().stream()
                .flatMap(List::stream)
                .map(RecipeIngredientEntity::getFoodCatalogItemId)
                .distinct()
                .toList();
        FoodData data = loadFoodData(foodIds, today);
        Map<UUID, RecipeComputation> result = new HashMap<>();
        for (RecipeEntity recipe : recipes) {
            result.put(recipe.getRecipeId(), computeOne(recipe,
                    ingredientsByRecipe.getOrDefault(recipe.getRecipeId(), List.of()), data));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RecipeComputation compute(RecipeEntity recipe) {
        return compute(List.of(recipe)).get(recipe.getRecipeId());
    }

    private FoodData loadFoodData(List<UUID> foodIds, LocalDate today) {
        Map<UUID, FoodCatalogItemEntity> foods = new HashMap<>();
        Map<UUID, FoodNutrientProfileEntity> profiles = new HashMap<>();
        Map<UUID, List<FoodNutrientDetailEntity>> details = new HashMap<>();
        Map<UUID, List<FoodServingEntity>> servings = new HashMap<>();
        if (!foodIds.isEmpty()) {
            foodRepository.findAllById(foodIds).forEach(food -> foods.put(food.getFoodId(), food));
            // The profiles come newest first, and the newest one of each food is the one the meal log uses.
            for (FoodNutrientProfileEntity profile : profileRepository.findActiveProfilesByFoodIdIn(foodIds, today)) {
                profiles.putIfAbsent(profile.getFoodId(), profile);
            }
            for (FoodNutrientDetailEntity detail : detailRepository.findActiveByFoodIdIn(foodIds, today)) {
                details.computeIfAbsent(detail.getFoodId(), id -> new ArrayList<>()).add(detail);
            }
            for (FoodServingEntity serving : servingRepository.findActiveByFoodIdIn(foodIds, today)) {
                servings.computeIfAbsent(serving.getFoodId(), id -> new ArrayList<>()).add(serving);
            }
        }
        Map<String, String> groups = new HashMap<>();
        for (FoodCategoryEntity category : categoryRepository.findAll()) {
            groups.put(category.getCode(), category.getFoodGroup());
        }
        return new FoodData(foods, profiles, details, servings, groups);
    }

    private RecipeComputation computeOne(RecipeEntity recipe, List<RecipeIngredientEntity> ingredients, FoodData data) {
        Totals totals = new Totals();
        Map<String, BigDecimal> groupGrams = new HashMap<>();
        Map<String, DetailTotal> detailTotals = new LinkedHashMap<>();
        List<IngredientComputation> computed = new ArrayList<>();
        boolean vegetarian = true;
        boolean vegan = true;
        BigDecimal totalGrams = BigDecimal.ZERO;
        for (RecipeIngredientEntity ingredient : ingredients) {
            FoodCatalogItemEntity food = data.foods.get(ingredient.getFoodCatalogItemId());
            FoodNutrientProfileEntity profile = data.profiles.get(ingredient.getFoodCatalogItemId());
            String categoryCode = food == null ? null : food.getCategoryCode();
            String foodGroup = categoryCode == null ? null : data.groups.getOrDefault(categoryCode, FOOD_GROUP_OTHER);
            BigDecimal multiplier = profile == null ? null : multiplier(ingredient, food, profile,
                    data.servings.getOrDefault(ingredient.getFoodCatalogItemId(), List.of()));
            BigDecimal grams = multiplier == null ? null : gramsOf(ingredient, food, profile, multiplier);
            if (categoryCode != null) {
                vegetarian &= !NON_VEGETARIAN_CATEGORIES.contains(categoryCode);
                vegan &= !NON_VEGAN_CATEGORIES.contains(categoryCode);
            }
            if (multiplier != null) {
                totals.add(profile, multiplier);
                for (FoodNutrientDetailEntity detail : data.details.getOrDefault(food.getFoodId(), List.of())) {
                    detailTotals.computeIfAbsent(detail.getNutrientCode(),
                            code -> new DetailTotal(detail.getDisplayName(), detail.getUnit())).add(detail.getAmount().multiply(multiplier));
                }
            }
            if (grams != null) {
                totalGrams = totalGrams.add(grams);
                groupGrams.merge(foodGroup == null ? FOOD_GROUP_OTHER : foodGroup, grams, BigDecimal::add);
            }
            computed.add(new IngredientComputation(ingredient.getIngredientId(), ingredient.getDisplayText(),
                    ingredient.getFoodCatalogItemId(), food == null ? null : food.getDisplayName(),
                    categoryCode, foodGroup, grams));
        }
        Map<String, BigDecimal> sortedGroups = new LinkedHashMap<>();
        groupGrams.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sortedGroups.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)));
        List<String> diet = new ArrayList<>();
        if (vegetarian) {
            diet.add(DIET_VEGETARIAN);
        }
        if (vegan) {
            diet.add(DIET_VEGAN);
        }
        return new RecipeComputation(recipe.getServings(), computed, totals, detailTotals, sortedGroups,
                totalGrams, tagsFor(totals, totalGrams), diet);
    }

    /**
     * How many basis amounts of the food profile the ingredient is. With grams the profile basis is a mass (or
     * a volume taken at the food's density), and without grams the quantity counts servings of the chosen
     * or default serving. Null when the amount cannot be worked out.
     */
    static BigDecimal multiplier(RecipeIngredientEntity ingredient, FoodCatalogItemEntity food,
                                 FoodNutrientProfileEntity profile, List<FoodServingEntity> servings) {
        BigDecimal basis = profile.getBasisQuantity();
        if (basis == null || basis.signum() <= 0) {
            return null;
        }
        if (ingredient.getGrams() != null) {
            if (isUnit(profile.getBasisUnit(), "g")) {
                return ingredient.getGrams().divide(basis, 6, RoundingMode.HALF_UP);
            }
            if (isUnit(profile.getBasisUnit(), "ml")) {
                BigDecimal density = food != null && food.getDensityGramsPerMl() != null
                        && food.getDensityGramsPerMl().signum() > 0 ? food.getDensityGramsPerMl() : BigDecimal.ONE;
                return ingredient.getGrams().divide(density, 6, RoundingMode.HALF_UP).divide(basis, 6, RoundingMode.HALF_UP);
            }
        }
        FoodServingEntity serving = pickServing(ingredient, servings);
        if (serving == null) {
            return null;
        }
        BigDecimal servingBasis = servingBasis(profile, serving);
        if (servingBasis == null) {
            return null;
        }
        return servingBasis.multiply(ingredient.getQuantity()).divide(basis, 6, RoundingMode.HALF_UP);
    }

    private static FoodServingEntity pickServing(RecipeIngredientEntity ingredient, List<FoodServingEntity> servings) {
        if (ingredient.getFoodServingId() != null) {
            return servings.stream()
                    .filter(serving -> serving.getServingId().equals(ingredient.getFoodServingId()))
                    .findFirst()
                    .orElse(null);
        }
        return servings.stream().filter(FoodServingEntity::isDefaultServing).findFirst()
                .orElse(servings.isEmpty() ? null : servings.get(0));
    }

    private static BigDecimal servingBasis(FoodNutrientProfileEntity profile, FoodServingEntity serving) {
        if (isUnit(profile.getBasisUnit(), serving.getUnit())) {
            return serving.getQuantity();
        }
        if (isUnit(profile.getBasisUnit(), "g")) {
            return serving.getGramsEquivalent();
        }
        if (isUnit(profile.getBasisUnit(), "ml")) {
            return serving.getMillilitersEquivalent();
        }
        return null;
    }

    private static BigDecimal gramsOf(RecipeIngredientEntity ingredient, FoodCatalogItemEntity food,
                                      FoodNutrientProfileEntity profile, BigDecimal multiplier) {
        if (ingredient.getGrams() != null) {
            return ingredient.getGrams();
        }
        BigDecimal basisAmount = profile.getBasisQuantity().multiply(multiplier);
        if (isUnit(profile.getBasisUnit(), "g")) {
            return basisAmount;
        }
        if (isUnit(profile.getBasisUnit(), "ml") && food != null && food.getDensityGramsPerMl() != null) {
            return basisAmount.multiply(food.getDensityGramsPerMl());
        }
        return null;
    }

    private static boolean isUnit(String left, String right) {
        return left != null && right != null && String.CASE_INSENSITIVE_ORDER.compare(left.trim(), right.trim()) == 0;
    }

    /** The tags a recipe earns per 100 g of its ingredients: high in protein, fibre, sugar or salt. */
    static List<String> tagsFor(Totals totals, BigDecimal totalGrams) {
        List<String> tags = new ArrayList<>();
        if (totalGrams.signum() <= 0) {
            return tags;
        }
        BigDecimal protein = per100g(totals.protein, totalGrams);
        BigDecimal energyFromProtein = totals.protein.multiply(KCAL_PER_GRAM_PROTEIN);
        if (protein.compareTo(HIGH_PROTEIN_MIN_PER_100G) >= 0 && totals.kcal.signum() > 0
                && energyFromProtein.compareTo(totals.kcal.multiply(PROTEIN_ENERGY_SHARE)) >= 0) {
            tags.add(TAG_HIGH_PROTEIN);
        }
        if (per100g(totals.fibre, totalGrams).compareTo(HIGH_FIBRE_MIN_PER_100G) >= 0) {
            tags.add(TAG_HIGH_FIBRE);
        }
        if (per100g(totals.sugar, totalGrams).compareTo(HIGH_SUGAR_OVER_PER_100G) > 0) {
            tags.add(TAG_HIGH_SUGAR);
        }
        if (per100g(totals.sodium, totalGrams).compareTo(HIGH_SALT_OVER_MG_PER_100G) > 0) {
            tags.add(TAG_HIGH_SALT);
        }
        return tags;
    }

    private static BigDecimal per100g(BigDecimal amount, BigDecimal totalGrams) {
        return amount.multiply(HUNDRED).divide(totalGrams, 4, RoundingMode.HALF_UP);
    }

    private record FoodData(
            Map<UUID, FoodCatalogItemEntity> foods,
            Map<UUID, FoodNutrientProfileEntity> profiles,
            Map<UUID, List<FoodNutrientDetailEntity>> details,
            Map<UUID, List<FoodServingEntity>> servings,
            Map<String, String> groups) {
    }

    /** What one ingredient resolved to: its food, plate group and weight. */
    public record IngredientComputation(
            UUID ingredientId,
            String displayText,
            UUID foodCatalogItemId,
            String foodName,
            String categoryCode,
            String foodGroup,
            BigDecimal grams) {
    }

    /** The sums over a whole recipe, before they are divided into servings. Unknown values count as zero. */
    static final class Totals {
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal fibre = BigDecimal.ZERO;
        BigDecimal sugar = BigDecimal.ZERO;
        BigDecimal sodium = BigDecimal.ZERO;
        BigDecimal saturatedFat = BigDecimal.ZERO;
        BigDecimal potassium = BigDecimal.ZERO;
        boolean potassiumKnown;

        void add(FoodNutrientProfileEntity profile, BigDecimal multiplier) {
            if (profile.getCalories() != null) {
                kcal = kcal.add(BigDecimal.valueOf(profile.getCalories()).multiply(multiplier));
            }
            protein = plus(protein, profile.getProteinGrams(), multiplier);
            carbs = plus(carbs, profile.getCarbsGrams(), multiplier);
            fat = plus(fat, profile.getFatGrams(), multiplier);
            fibre = plus(fibre, profile.getFiberGrams(), multiplier);
            sugar = plus(sugar, profile.getSugarGrams(), multiplier);
            sodium = plus(sodium, profile.getSodiumMg(), multiplier);
            saturatedFat = plus(saturatedFat, profile.getSaturatedFatGrams(), multiplier);
            if (profile.getPotassiumMg() != null) {
                potassiumKnown = true;
                potassium = plus(potassium, profile.getPotassiumMg(), multiplier);
            }
        }

        private static BigDecimal plus(BigDecimal total, BigDecimal value, BigDecimal multiplier) {
            return value == null ? total : total.add(value.multiply(multiplier));
        }
    }

    /** The summed amount of one vitamin or mineral over a whole recipe. */
    static final class DetailTotal {
        final String displayName;
        final String unit;
        BigDecimal amount = BigDecimal.ZERO;

        DetailTotal(String displayName, String unit) {
            this.displayName = displayName;
            this.unit = unit;
        }

        void add(BigDecimal value) {
            amount = amount.add(value);
        }
    }

    /**
     * The result for one recipe. The per-serving figures divide the whole-recipe sums by the number of
     * servings, and a recipe with no usable serving count reads as one serving.
     */
    public static final class RecipeComputation {

        private final BigDecimal servings;
        private final List<IngredientComputation> ingredients;
        private final Totals totals;
        private final Map<String, DetailTotal> details;
        private final Map<String, BigDecimal> groupsGrams;
        private final BigDecimal totalGrams;
        private final List<String> tags;
        private final List<String> dietFlags;

        RecipeComputation(BigDecimal servings, List<IngredientComputation> ingredients, Totals totals,
                          Map<String, DetailTotal> details, Map<String, BigDecimal> groupsGrams,
                          BigDecimal totalGrams, List<String> tags, List<String> dietFlags) {
            this.servings = servings == null || servings.signum() <= 0 ? BigDecimal.ONE : servings;
            this.ingredients = List.copyOf(ingredients);
            this.totals = totals;
            this.details = details;
            this.groupsGrams = groupsGrams;
            this.totalGrams = totalGrams;
            this.tags = List.copyOf(tags);
            this.dietFlags = List.copyOf(dietFlags);
        }

        public List<IngredientComputation> ingredients() {
            return ingredients;
        }

        /** Grams per food group over the whole recipe, the largest group first. */
        public Map<String, BigDecimal> groupsGrams() {
            return java.util.Collections.unmodifiableMap(groupsGrams);
        }

        /** The names of the three food groups with the most grams. */
        public List<String> topFoodGroups() {
            return groupsGrams.keySet().stream().limit(TOP_GROUPS).toList();
        }

        public BigDecimal totalGrams() {
            return totalGrams;
        }

        public List<String> tags() {
            return tags;
        }

        public List<String> dietFlags() {
            return dietFlags;
        }

        public int kcalPerServing() {
            return perServing(totals.kcal, 0).intValue();
        }

        public BigDecimal proteinPerServing() {
            return perServing(totals.protein, 2);
        }

        public BigDecimal carbsPerServing() {
            return perServing(totals.carbs, 2);
        }

        public BigDecimal fatPerServing() {
            return perServing(totals.fat, 2);
        }

        public BigDecimal fibrePerServing() {
            return perServing(totals.fibre, 2);
        }

        public BigDecimal sugarPerServing() {
            return perServing(totals.sugar, 2);
        }

        public BigDecimal sodiumPerServing() {
            return perServing(totals.sodium, 2);
        }

        public BigDecimal saturatedFatPerServing() {
            return perServing(totals.saturatedFat, 2);
        }

        /** Potassium from the profile column, or from the detail rows when no profile states it; null when neither does. */
        public BigDecimal potassiumPerServing() {
            if (totals.potassiumKnown) {
                return perServing(totals.potassium, 2);
            }
            DetailTotal detail = details.get(POTASSIUM);
            return detail == null ? null : perServing(detail.amount, 2);
        }

        /** The vitamins and minerals per serving in catalogue order, with the code, name, amount and unit. */
        public List<NutrientPerServing> nutrientsPerServing() {
            List<NutrientPerServing> result = new ArrayList<>();
            for (Map.Entry<String, DetailTotal> entry : details.entrySet()) {
                result.add(new NutrientPerServing(entry.getKey(), entry.getValue().displayName,
                        perServing(entry.getValue().amount, 2), entry.getValue().unit));
            }
            return result;
        }

        private BigDecimal perServing(BigDecimal total, int scale) {
            return total.divide(servings, scale, RoundingMode.HALF_UP);
        }
    }

    /** One vitamin or mineral in one serving. */
    public record NutrientPerServing(String code, String displayName, BigDecimal amount, String unit) {
    }
}
