package com.fitsync.recommendation.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;

/**
 * One cached recommendation as served by the read API: the structured rule facts, the
 * phrased narrative, and freshness metadata.
 */
public record RecommendationResponse(
        String type,
        RecommendationSource source,
        Map<String, Object> facts,
        String narrative,
        Instant generatedAt,
        Instant expiresAt) {

    private static final TypeReference<Map<String, Object>> PAYLOAD_SHAPE = new TypeReference<>() {
    };

    public RecommendationResponse {
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts == null ? Map.of() : facts));
    }

    public static RecommendationResponse from(RecommendationResultEntity entity, ObjectMapper objectMapper) {
        Map<String, Object> payload = parsePayload(entity, objectMapper);
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = payload.get("facts") instanceof Map<?, ?> factsMap
                ? (Map<String, Object>) factsMap
                : Map.of();
        return new RecommendationResponse(
                entity.getType(),
                entity.getSource(),
                facts,
                payload.get("narrative") instanceof String narrative ? narrative : null,
                entity.getGeneratedAt(),
                entity.getExpiresAt());
    }

    private static Map<String, Object> parsePayload(RecommendationResultEntity entity, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(entity.getPayload(), PAYLOAD_SHAPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Stored recommendation payload is not valid JSON for type " + entity.getType(), ex);
        }
    }
}
