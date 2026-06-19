package com.fitsync.nutrition.external;

import java.util.Optional;

import com.fitsync.nutrition.dto.FoodDetailView;

/** A fallback food source used when a barcode (or search) misses the local catalog. */
public interface ExternalFoodSource {

    /**
     * Looks up a product by barcode in the external source. Returns empty when the feature is
     * disabled, the product is not found, or the lookup fails — callers degrade to "not found".
     */
    Optional<FoodDetailView> findByBarcode(String barcode);
}
