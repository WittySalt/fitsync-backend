package com.fitsync.nutrition.external;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.config.ExternalFoodProperties;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final Object throttleMonitor = new Object();
    private Instant lastRequestAt = Instant.EPOCH;

    @Autowired
    public OpenFoodFactsFoodSource(ExternalFoodProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)));
    }

    OpenFoodFactsFoodSource(ExternalFoodProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
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
            throttle();
            String body = restClient.get()
                    .uri("/api/v2/product/{barcode}.json?fields=code,product_name,brands,categories_tags,categories,nutriments",
                            barcode)
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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("OpenFoodFacts barcode lookup failed", ex);
            return Optional.empty();
        } catch (Exception ex) {
            // Constant message + throwable overload — never interpolate user input (CRLF/log injection).
            log.warn("OpenFoodFacts barcode lookup failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public List<FoodSearchResultView> search(String query, int limit) {
        if (!properties.enabled() || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            throttle();
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi/search.pl")
                            .queryParam("search_terms", query)
                            .queryParam("search_simple", "1")
                            .queryParam("action", "process")
                            .queryParam("json", "1")
                            .queryParam("page_size", limit)
                            .queryParam("fields", "code,product_name,brands,categories_tags,categories,nutriments")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return List.of();
            }
            JsonNode products = objectMapper.readTree(body).path("products");
            if (!products.isArray()) {
                return List.of();
            }
            Map<String, FoodSearchResultView> resultsByBarcode = new LinkedHashMap<>();
            for (JsonNode product : products) {
                String code = text(product, "code");
                if (code == null || resultsByBarcode.containsKey(code)) {
                    continue;
                }
                FoodDetailView detail = toFoodDetailView(product, code);
                resultsByBarcode.put(code, toFoodSearchResultView(detail));
                if (resultsByBarcode.size() >= limit) {
                    break;
                }
            }
            return new ArrayList<>(resultsByBarcode.values());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("OpenFoodFacts search failed", ex);
            return List.of();
        } catch (Exception ex) {
            log.warn("OpenFoodFacts search failed", ex);
            return List.of();
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
                categoryCode(product),
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

    private static SimpleClientHttpRequestFactory requestFactory(ExternalFoodProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private void throttle() throws InterruptedException {
        Duration minInterval = properties.minRequestInterval();
        if (minInterval.isZero()) {
            return;
        }
        long waitMillis;
        synchronized (throttleMonitor) {
            Instant now = Instant.now(Clock.systemUTC());
            Instant nextAllowed = lastRequestAt.plus(minInterval);
            if (now.isBefore(nextAllowed)) {
                waitMillis = Duration.between(now, nextAllowed).toMillis();
                lastRequestAt = nextAllowed;
            } else {
                waitMillis = 0L;
                lastRequestAt = now;
            }
        }
        if (waitMillis > 0L) {
            Thread.sleep(waitMillis);
        }
    }

    private FoodSearchResultView toFoodSearchResultView(FoodDetailView detail) {
        String basisLabel = detail.basisQuantity() == null || detail.basisUnit() == null
                ? null
                : detail.basisQuantity().stripTrailingZeros().toPlainString() + " " + detail.basisUnit();
        return new FoodSearchResultView(
                detail.foodId(),
                detail.displayName(),
                detail.brandName(),
                detail.categoryCode(),
                null,
                detail.scope(),
                detail.source(),
                detail.verified(),
                detail.calories(),
                basisLabel,
                detail.primaryBarcode());
    }

    private String categoryCode(JsonNode product) {
        List<String> tags = new ArrayList<>();
        JsonNode categoryTags = product.path("categories_tags");
        if (categoryTags.isArray()) {
            for (JsonNode tag : categoryTags) {
                if (tag.isTextual()) {
                    tags.add(tag.asText());
                }
            }
        }
        String categories = text(product, "categories");
        if (categories != null) {
            tags.add(categories);
        }
        if (containsAny(tags, "en:yogurts", "en:dairies", "en:cheeses", "en:milks")) {
            return "DAIRY";
        }
        if (containsAny(tags, "en:meats", "en:poultries", "en:beef", "en:chicken", "en:pork")) {
            return "MEAT_POULTRY";
        }
        if (containsAny(tags, "en:fishes", "en:seafood", "en:seafoods")) {
            return "SEAFOOD";
        }
        if (containsAny(tags, "en:eggs")) {
            return "EGGS";
        }
        if (containsAny(tags, "en:plant-based-foods", "en:tofu", "en:legumes")) {
            return "PLANT_PROTEIN";
        }
        if (containsAny(tags, "en:cereals-and-potatoes", "en:breads", "en:pastas", "en:rices", "en:grains")) {
            return "GRAINS";
        }
        if (containsAny(tags, "en:vegetables")) {
            return "VEGETABLES";
        }
        if (containsAny(tags, "en:fruits")) {
            return "FRUITS";
        }
        if (containsAny(tags, "en:nuts", "en:seeds")) {
            return "NUTS_SEEDS";
        }
        if (containsAny(tags, "en:fats", "en:oils")) {
            return "FATS_OILS";
        }
        if (containsAny(tags, "en:beverages", "en:drinks")) {
            return "BEVERAGES";
        }
        if (containsAny(tags, "en:snacks")) {
            return "SNACKS";
        }
        if (containsAny(tags, "en:sweets", "en:chocolates", "en:candies")) {
            return "SWEETS";
        }
        if (containsAny(tags, "en:soups", "en:sauces")) {
            return "SOUPS_SAUCES";
        }
        if (containsAny(tags, "en:spices", "en:herbs")) {
            return "SPICES";
        }
        if (containsAny(tags, "en:prepared-meals", "en:prepared-foods")) {
            return "PREPARED";
        }
        return "OTHER";
    }

    private boolean containsAny(List<String> values, String... candidates) {
        for (String value : values) {
            for (String candidate : candidates) {
                if (value.contains(candidate)) {
                    return true;
                }
            }
        }
        return false;
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
