package com.fitsync.nutrition.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventPublisher;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.RecipeCookedPayload;
import com.fitsync.nutrition.dto.CreateRecipeCookRequest;
import com.fitsync.nutrition.dto.RecipeCookView;
import com.fitsync.nutrition.entity.RecipeCookEntity;
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
    private static final String NUTRITION_EVENTS_TOPIC = "nutrition-events";
    private static final String SOURCE = "nutrition-service";

    private final RecipeCookRepository cookRepository;
    private final RecipeLibraryService libraryService;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public RecipeCookService(RecipeCookRepository cookRepository, RecipeLibraryService libraryService,
                             DomainEventPublisher domainEventPublisher, Clock clock) {
        this.cookRepository = cookRepository;
        this.libraryService = libraryService;
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
                cook.getFinishedAt());
    }
}
