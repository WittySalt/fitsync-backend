package com.fitsync.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fitsync.recommendation.entity.RecommendationContextEntity;

/**
 * The §5.1 change-detection digest: a SHA-256 over every rule-relevant context field.
 * Stored on each result; the AI tier compares it before regenerating so an unchanged
 * context never pays for a second model call.
 */
public final class ContextHashing {

    private ContextHashing() {
    }

    public static String hash(RecommendationContextEntity context) {
        String canonical = String.join("|",
                String.valueOf(context.getCurrentGoalType()),
                String.valueOf(context.getCurrentGoalTargetValue()),
                String.valueOf(context.getCurrentGoalTargetUnit()),
                String.valueOf(context.getCurrentGoalTargetDate()),
                String.valueOf(context.getGoalBaselineWeightKg()),
                String.valueOf(context.getGoalBaselineOn()),
                String.valueOf(context.getHeightCm()),
                String.valueOf(context.getActivityLevel()),
                String.valueOf(context.getLatestWeightKg()),
                String.valueOf(context.getLatestBodyFatRate()),
                String.valueOf(context.getWorkoutDaysPerWeek()),
                String.valueOf(context.getDietaryPreference()),
                String.valueOf(context.getFocusAreas()),
                String.valueOf(context.getEquipmentInventory()),
                String.valueOf(context.getRecentWorkoutCount()),
                String.valueOf(context.getRecentWorkoutMinutes()),
                String.valueOf(context.getRecentActiveDays()),
                String.valueOf(context.getRecentWeekStart()),
                String.valueOf(context.getTodayDate()),
                String.valueOf(context.getTodayCaloriesConsumed()),
                String.valueOf(context.getTodayProteinGrams()),
                String.valueOf(context.getCurrentStreakDays()),
                String.valueOf(context.getTimezone()));
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
