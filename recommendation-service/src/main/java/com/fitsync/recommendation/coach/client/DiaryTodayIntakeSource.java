package com.fitsync.recommendation.coach.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.service.TodayIntakeSource;
import org.springframework.stereotype.Component;

/**
 * Today's intake from nutrition-service's daily summary, the same figures the Diary shows.
 */
@Component
public class DiaryTodayIntakeSource implements TodayIntakeSource {

    private final NutritionCatalogueClient nutritionClient;
    private final ObjectReader reader;

    public DiaryTodayIntakeSource(NutritionCatalogueClient nutritionClient, ObjectMapper objectMapper) {
        this.nutritionClient = nutritionClient;
        this.reader = objectMapper.reader();
    }

    @Override
    public TodayIntake read(String bearerToken, LocalDate date, ZoneId zone) {
        if (bearerToken == null || bearerToken.isBlank() || date == null) {
            return TodayIntake.unknown();
        }
        try {
            String body = nutritionClient.dailySummary(bearerToken, date, zoneName(zone));
            return body == null ? TodayIntake.unknown() : fromSummary(reader.readTree(body), date);
        } catch (JsonProcessingException | RuntimeException ex) {
            return TodayIntake.unknown();
        }
    }

    /**
     * The intake in a daily summary already read, so a caller that also needs the rest of the summary
     * reads it once. A summary without a calorie total is unknown.
     */
    public static TodayIntake fromSummary(JsonNode summary, LocalDate date) {
        if (summary == null || !summary.path("totalCalories").isNumber()) {
            return TodayIntake.unknown();
        }
        JsonNode protein = summary.path("totalProteinGrams");
        BigDecimal proteinGrams = protein.isNumber() ? protein.decimalValue() : BigDecimal.ZERO;
        return new TodayIntake(date, summary.path("totalCalories").asInt(), proteinGrams);
    }

    /** The zone as the other services expect it: UTC is written "UTC", not the offset id "Z". */
    static String zoneName(ZoneId zone) {
        if (zone == null) {
            return null;
        }
        return zone.getId().equals("Z") ? "UTC" : zone.getId();
    }
}
