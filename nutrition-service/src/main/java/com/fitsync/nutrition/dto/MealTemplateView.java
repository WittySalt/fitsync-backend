package com.fitsync.nutrition.dto;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;

public record MealTemplateView(
        UUID templateId,
        String name,
        MealType mealType,
        String note,
        Integer totalCalories,
        List<MealTemplateItemView> items) {

    public MealTemplateView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public List<MealTemplateItemView> items() {
        return List.copyOf(items);
    }
}
