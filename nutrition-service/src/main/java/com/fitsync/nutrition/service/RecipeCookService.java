package com.fitsync.nutrition.service;

import java.time.Clock;
import java.time.Duration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventPublisher;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.RecipeCookedPayload;
import com.fitsync.nutrition.dto.CookLeftoverView;
import com.fitsync.nutrition.dto.CreateRecipeCookRequest;
import com.fitsync.nutrition.dto.LeftoverMealRequest;
import com.fitsync.nutrition.dto.LeftoverMealView;
import com.fitsync.nutrition.dto.RecipeMealRequest;
import com.fitsync.nutrition.dto.RecipeCookView;
import com.fitsync.nutrition.entity.RecipeCookEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.repository.RecipeCookRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ConflictException;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The record of "I cooked this", kept apart from eating it. A cook is written for the caller only, for a
 * recipe the caller can see, and a retry with the same client id answers with the record that was written
 * first. A newly written cook publishes RecipeCooked through the outbox, in the same transaction, so that
 * analytics can count cooks for the kitchen badges; a retry publishes nothing.
 */
@Service
public class RecipeCookService {

    static final Duration ALLOWED_SKEW = Duration.ofMinutes(5);
    static final String RECIPE_COOKED = "RecipeCooked";
    /** Cooks of this many days back still offer their leftovers. */
    static final Duration LEFTOVER_WINDOW = Duration.ofDays(7);
    private static final String NUTRITION_EVENTS_TOPIC = "nutrition-events";
    private static final String SOURCE = "nutrition-service";

    private final RecipeCookRepository cookRepository;
    private final RecipeLibraryService libraryService;
    private final RecipeInsightService insightService;
    private final NutritionService nutritionService;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public RecipeCookService(RecipeCookRepository cookRepository, RecipeLibraryService libraryService,
                             RecipeInsightService insightService, NutritionService nutritionService,
                             DomainEventPublisher domainEventPublisher, Clock clock) {
        this.cookRepository = cookRepository;
        this.libraryService = libraryService;
        this.insightService = insightService;
        this.nutritionService = nutritionService;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /** The view and whether it was newly written, so that the endpoint can answer 201 or 200. */
    public record Recorded(RecipeCookView view, boolean created) {
    }

    @Transactional
    public Recorded record(CurrentUserPrincipal principal, UUID recipeId, CreateRecipeCookRequest request) {
        UUID userId = principal.userId();
        // The recipe must be one the caller may see, which answers 404 for anything else.
        libraryService.findVisible(userId, recipeId);
        var existing = cookRepository.findByUserIdAndClientCookId(userId, request.clientCookId());
        if (existing.isPresent()) {
            RecipeCookEntity cook = existing.get();
            if (cook.getDeletedAt() != null || !cook.getRecipeId().equals(recipeId)) {
                throw new ConflictException("That cook id was already used");
            }
            return new Recorded(toView(cook), false);
        }
        Instant now = clock.instant();
        Instant finishedAt = request.finishedAt() == null ? now : request.finishedAt();
        if (finishedAt.isAfter(now.plus(ALLOWED_SKEW))) {
            throw new BadRequestException("finishedAt must not be in the future");
        }
        if (request.startedAt() != null && request.startedAt().isAfter(finishedAt)) {
            throw new BadRequestException("startedAt must not be after finishedAt");
        }
        RecipeCookEntity cook = new RecipeCookEntity();
        cook.setCookId(UUID.randomUUID());
        cook.setUserId(userId);
        cook.setRecipeId(recipeId);
        cook.setClientCookId(request.clientCookId());
        cook.setServings(request.servings());
        cook.setServingsLeft(servingsLeft(request.servings(), request.servingsLogged()));
        cook.setStartedAt(request.startedAt());
        cook.setFinishedAt(finishedAt);
        cook.setCreatedAt(now);
        RecipeCookEntity saved = cookRepository.save(cook);
        publishRecipeCooked(saved, now);
        return new Recorded(toView(saved), true);
    }

    private void publishRecipeCooked(RecipeCookEntity cook, Instant now) {
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "RecipeCook", cook.getCookId().toString(),
                new FitSyncDomainEvent<>(
                        UUID.randomUUID(),
                        RECIPE_COOKED,
                        1,
                        now,
                        SOURCE,
                        cook.getUserId(),
                        new RecipeCookedPayload(cook.getCookId(), cook.getRecipeId(), cook.getServings(),
                                cook.getFinishedAt())));
    }

    /** Whole servings left: cooked minus logged rounded down, never below 0; nothing logged-info means none. */
    static int servingsLeft(BigDecimal cooked, BigDecimal logged) {
        if (logged == null) {
            return 0;
        }
        return Math.max(0, cooked.subtract(logged).setScale(0, RoundingMode.FLOOR).intValue());
    }

    /** The caller's cooks of the last 7 days with servings left, newest first; unreadable recipes are left out. */
    @Transactional(readOnly = true)
    public List<CookLeftoverView> leftovers(CurrentUserPrincipal principal) {
        UUID userId = principal.userId();
        Instant since = clock.instant().minus(LEFTOVER_WINDOW);
        List<RecipeCookEntity> cooks = cookRepository
                .findByUserIdAndDeletedAtIsNullAndServingsLeftGreaterThanAndFinishedAtGreaterThanEqualOrderByFinishedAtDesc(
                        userId, 0, since);
        if (cooks.isEmpty()) {
            return List.of();
        }
        Map<UUID, RecipeEntity> recipes = libraryService.findVisible(userId,
                cooks.stream().map(RecipeCookEntity::getRecipeId).distinct().toList());
        Map<UUID, RecipeInsightService.RecipeComputation> computations = insightService.compute(recipes.values());
        return cooks.stream()
                .filter(cook -> recipes.containsKey(cook.getRecipeId()))
                .map(cook -> {
                    RecipeEntity recipe = recipes.get(cook.getRecipeId());
                    RecipeInsightService.RecipeComputation computation = computations.get(recipe.getRecipeId());
                    return new CookLeftoverView(cook.getCookId(), recipe.getRecipeId(), recipe.getName(),
                            recipe.getImageUrl(), cook.getFinishedAt(), cook.getServingsLeft(),
                            computation == null ? null : computation.kcalPerServing());
                })
                .toList();
    }

    /**
     * Logs servings of the caller's own cook as a meal of the recipe and takes them off the leftovers. Somebody
     * else's, an unknown or a deleted cook answers 404; fewer servings left than asked answers 409. Both steps
     * run in one transaction, so a meal that cannot be written gives the servings back.
     */
    @Transactional
    public LeftoverMealView logLeftover(CurrentUserPrincipal principal, UUID cookId, LeftoverMealRequest request,
                                        String timezone) {
        UUID userId = principal.userId();
        RecipeCookEntity cook = cookRepository.findByCookIdAndUserIdAndDeletedAtIsNull(cookId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cook not found"));
        int servings = request.servings() == null ? 1 : request.servings();
        // The recipe may have been deleted since the cook, which answers 404 as for any recipe the caller cannot see.
        libraryService.findVisible(userId, cook.getRecipeId());
        if (cookRepository.takeLeftover(cookId, userId, servings) == 0) {
            throw new ConflictException("No leftover servings are left for that cook");
        }
        Instant consumedAt = request.consumedAt() == null ? clock.instant() : request.consumedAt();
        var meal = nutritionService.createMealFromRecipe(principal, cook.getRecipeId(),
                new RecipeMealRequest(BigDecimal.valueOf(servings), request.mealType(), consumedAt), timezone);
        int left = cookRepository.findByCookIdAndUserIdAndDeletedAtIsNull(cookId, userId)
                .map(RecipeCookEntity::getServingsLeft).orElse(0);
        return new LeftoverMealView(cookId, left, meal);
    }

    /** Marks the caller's own cook as deleted; anybody else's, or one already deleted, answers 404. */
    @Transactional
    public void delete(CurrentUserPrincipal principal, UUID cookId) {
        RecipeCookEntity cook = cookRepository.findByCookIdAndUserIdAndDeletedAtIsNull(cookId, principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Cook not found"));
        cook.setDeletedAt(clock.instant());
        cookRepository.save(cook);
    }

    private static RecipeCookView toView(RecipeCookEntity cook) {
        return new RecipeCookView(cook.getCookId(), cook.getRecipeId(), cook.getServings(), cook.getStartedAt(),
                cook.getFinishedAt(), cook.getServingsLeft());
    }
}
