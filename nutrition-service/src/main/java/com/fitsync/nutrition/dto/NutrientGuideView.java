package com.fitsync.nutrition.dto;

import java.util.List;

/** The plain-English guide of a vitamin or mineral. */
public record NutrientGuideView(
        String whatItDoes,
        String tooLittleTooMuch,
        String whoNeedsMore,
        List<String> tips,
        String sourceLabel) {

    public NutrientGuideView {
        tips = tips == null ? List.of() : List.copyOf(tips);
    }

    @Override
    public List<String> tips() {
        return List.copyOf(tips);
    }
}
