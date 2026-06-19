package com.fitsync.nutrition.external;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.config.ExternalFoodProperties;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.dto.FoodDetailView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenFoodFacts barcode fallback. Maps a product's per-100g nutriments to a non-persisted
 * {@link FoodDetailView} (foodId null, source IMPORTED) the caller can show or log as a manual item.
 * No-ops (returns empty) when disabled or on any error, so a miss degrades to "not found".
 */
@Component
public class OpenFoodFactsFoodSource implements ExternalFoodSource {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsFoodSource.class);

    private final ExternalFoodProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenFoodFactsFoodSource(ExternalFoodProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }

    @Override
    public Optional<FoodDetailView> findByBarcode(String barcode) {
        if (!properties.enabled() || barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        try {
            String body = restClient.get()
                    .uri("/api/v2/product/{barcode}.json?fields=code,product_name,brands,nutriments", barcode)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            if (root.path("status").asInt(0) != 1) {
                return Optional.empty();
            }
            return Optional.of(toFoodDetailView(root.path("product"), barcode));
        } catch (Exception ex) {
            log.warn("OpenFoodFacts lookup failed for barcode {}: {}", barcode, ex.toString());
            return Optional.empty();
        }
    }

    /** Maps an OpenFoodFacts product node (per-100g nutriments) to a non-persisted FoodDetailView. */
    FoodDetailView toFoodDetailView(JsonNode product, String barcode) {
        String name = text(product, "product_name");
        String displayName = name != null ? name : "Unknown product";
        JsonNode nutriments = product.path("nutriments");
        BigDecimal sodiumGrams = rawDecimal(nutriments, "sodium_100g");
        BigDecimal sodiumMg = sodiumGrams == null ? null
                : sodiumGrams.multiply(new BigDecimal("1000")).setScale(2, java.math.RoundingMode.HALF_UP);
        return new FoodDetailView(
                null,
                displayName,
                displayName.toLowerCase(Locale.ROOT),
                firstBrand(text(product, "brands")),
                null,
                FoodCatalogScope.GLOBAL,
                null,
                FoodCreationMode.MANUAL,
                FoodCatalogSource.IMPORTED,
                false,
                null,
                intValue(nutriments, "energy-kcal_100g"),
                decimal(nutriments, "proteins_100g"),
                decimal(nutriments, "carbohydrates_100g"),
                decimal(nutriments, "fat_100g"),
                decimal(nutriments, "saturated-fat_100g"),
                null,
                decimal(nutriments, "fiber_100g"),
                decimal(nutriments, "sugars_100g"),
                sodiumMg,
                null,
                null,
                new BigDecimal("100"),
                "g",
                barcode,
                List.of(),
                List.of());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstBrand(String brands) {
        if (brands == null) {
            return null;
        }
        return brands.split(",")[0].trim();
    }

    private BigDecimal rawDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber() && !value.isTextual()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        BigDecimal value = rawDecimal(node, field);
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Integer intValue(JsonNode node, String field) {
        BigDecimal value = rawDecimal(node, field);
        return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
