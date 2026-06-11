package com.fitsync.user.dto;

public record UserPreferencesView(
        AppPreferencesView app,
        TrainingPreferencesView training,
        NutritionPreferencesView nutrition,
        NotificationPreferencesView notifications) {
}
