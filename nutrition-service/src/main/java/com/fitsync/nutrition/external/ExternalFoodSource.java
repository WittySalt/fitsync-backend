package com.fitsync.nutrition.external;

import java.util.Optional;
import java.util.List;

import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchResultView;

/** A fallback food source used when a barcode (or search) misses the local catalog. */
public interface ExternalFoodSource {

    /**
     * Looks up a product by barcode in the external source. Returns empty when the feature is
     * disabled, the product is not found, or the lookup fails — callers degrade to "not found".
     */
    Optional<FoodDetailView> findByBarcode(String barcode);

    /**
     * Searches the external source. Returns an empty list when disabled, no products are found,
     * or the lookup fails.
     */
    default List<FoodSearchResultView> search(String query, int limit) {
        return List.of();
    }
}
