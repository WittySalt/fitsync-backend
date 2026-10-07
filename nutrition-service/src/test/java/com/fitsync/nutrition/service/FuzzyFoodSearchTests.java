package com.fitsync.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

class FuzzyFoodSearchTests {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");

    private final FoodCatalogItemRepository repository = mock(FoodCatalogItemRepository.class);
    private final UUID user = UUID.randomUUID();

    @Test
    void itAsksTheDatabaseWithTheLoweredQueryAndDropsFoodsAlreadyFound() {
        UUID found = UUID.randomUUID();
        UUID extra = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(repository.findSimilarFoodIds(eq(user), eq("chiken"), eq(TODAY), eq(0.3), eq(3)))
                .thenReturn(List.of(found, extra, other));

        List<UUID> ids = new FuzzyFoodSearch(repository, true)
                .similarFoodIds(user, "  Chiken ", TODAY, Set.of(found), 2);

        // Asked for the page room plus the foods to skip, answered with the page room.
        assertThat(ids).containsExactly(extra, other);
    }

    @Test
    void itStaysQuietWhenOffTooShortOrWithoutRoom() {
        FuzzyFoodSearch off = new FuzzyFoodSearch(repository, false);
        FuzzyFoodSearch on = new FuzzyFoodSearch(repository, true);

        assertThat(off.similarFoodIds(user, "chiken", TODAY, Set.of(), 5)).isEmpty();
        assertThat(on.similarFoodIds(user, "ch", TODAY, Set.of(), 5)).isEmpty();
        assertThat(on.similarFoodIds(user, null, TODAY, Set.of(), 5)).isEmpty();
        assertThat(on.similarFoodIds(user, "chiken", TODAY, Set.of(), 0)).isEmpty();

        verify(repository, never()).findSimilarFoodIds(any(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void aDatabaseThatCannotDoTrigramsLeavesTheExactResultsAlone() {
        when(repository.findSimilarFoodIds(any(), any(), any(), anyDouble(), anyInt()))
                .thenThrow(new InvalidDataAccessResourceUsageException("function similarity does not exist"));

        assertThat(new FuzzyFoodSearch(repository, true).similarFoodIds(user, "chiken", TODAY, Set.of(), 5))
                .isEmpty();
    }

    @Test
    void mergingKeepsTheExactFoodsFirstDropsRepeatsAndStopsAtThePage() {
        FoodCatalogItemEntity a = food("A");
        FoodCatalogItemEntity b = food("B");
        FoodCatalogItemEntity c = food("C");
        FoodCatalogItemEntity d = food("D");

        assertThat(NutritionServiceImpl.mergeSimilar(List.of(a, b), List.of(b, c, d), 4))
                .containsExactly(a, b, c, d);
        assertThat(NutritionServiceImpl.mergeSimilar(List.of(a, b), List.of(b, c, d), 3))
                .containsExactly(a, b, c);
        assertThat(NutritionServiceImpl.mergeSimilar(List.of(), List.of(c), 20)).containsExactly(c);
    }

    private static FoodCatalogItemEntity food(String name) {
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(UUID.randomUUID());
        food.setDisplayName(name);
        return food;
    }
}
