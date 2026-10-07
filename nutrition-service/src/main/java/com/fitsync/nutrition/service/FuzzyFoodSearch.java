package com.fitsync.nutrition.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The typo-tolerant part of the food search: when the exact and prefix search comes back short, it names more
 * foods by trigram similarity. It is switched off with {@code fitsync.nutrition.fuzzy-search-enabled=false}
 * (H2 cannot run pg_trgm), and any database error, such as the extension not being installed, leaves the exact
 * results as they are instead of failing the search.
 */
@Component
public class FuzzyFoodSearch {

    private static final Logger LOG = LoggerFactory.getLogger(FuzzyFoodSearch.class);

    /** The similarity a name must beat. */
    static final double MIN_SIMILARITY = 0.3;
    /** A query shorter than this has too few trigrams to compare. */
    static final int MIN_QUERY_LENGTH = 3;

    private final FoodCatalogItemRepository foodRepository;
    private final boolean enabled;

    public FuzzyFoodSearch(FoodCatalogItemRepository foodRepository,
                           @Value("${fitsync.nutrition.fuzzy-search-enabled:true}") boolean enabled) {
        this.foodRepository = foodRepository;
        this.enabled = enabled;
    }

    /**
     * Up to {@code limit} food ids close to the query, best first, none of them in {@code alreadyFound}. Empty
     * when the fallback is off, the query is too short or the database cannot answer.
     */
    public List<UUID> similarFoodIds(UUID userId, String query, LocalDate onDate, Set<UUID> alreadyFound,
                                     int limit) {
        if (!enabled || limit <= 0 || query == null || query.strip().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        Set<UUID> skip = new HashSet<>(alreadyFound);
        try {
            return foodRepository.findSimilarFoodIds(userId, query.strip().toLowerCase(java.util.Locale.ROOT), onDate,
                            MIN_SIMILARITY, limit + skip.size())
                    .stream()
                    .filter(id -> !skip.contains(id))
                    .distinct()
                    .limit(limit)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("Fuzzy food search skipped: {}", ex.getMessage());
            return List.of();
        }
    }
}
