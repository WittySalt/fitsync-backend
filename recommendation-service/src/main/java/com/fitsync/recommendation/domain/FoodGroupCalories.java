package com.fitsync.recommendation.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Encoding of the context's weekly food-group accumulation
 * ({@code week_food_group_calories}): a JSON object mapping the internal
 * {@code food_categories} code to kcal. Keys are written in sorted order so the JSON is
 * canonical — the change-detection hash depends on it.
 */
public final class FoodGroupCalories {

    private static final TypeReference<Map<String, Integer>> MAP_TYPE = new TypeReference<>() {
    };

    private FoodGroupCalories() {
    }

    /** Empty and unreadable both decode to an empty map — the column is best-effort. */
    public static Map<String, Integer> read(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /** Canonical (key-sorted) JSON, or null for an empty map. */
    public static String write(ObjectMapper objectMapper, Map<String, Integer> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(groups));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize food-group calories", ex);
        }
    }

    public static Map<String, Integer> merge(Map<String, Integer> current, Map<String, Integer> increment) {
        Map<String, Integer> merged = new LinkedHashMap<>(current);
        increment.forEach((group, calories) -> {
            if (group != null && calories != null) {
                merged.merge(group, calories, Integer::sum);
            }
        });
        return merged;
    }
}
