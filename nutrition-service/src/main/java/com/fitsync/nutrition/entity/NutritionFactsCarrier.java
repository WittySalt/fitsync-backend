package com.fitsync.nutrition.entity;

import java.math.BigDecimal;

public interface NutritionFactsCarrier {

    NutritionFacts nutritionFactsSnapshot();

    void replaceNutritionFacts(NutritionFacts nutritionFacts);

    default Integer getCalories() {
        return nutritionFactsSnapshot().getCalories();
    }

    default void setCalories(Integer calories) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setCalories(calories);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getProteinGrams() {
        return nutritionFactsSnapshot().getProteinGrams();
    }

    default void setProteinGrams(BigDecimal proteinGrams) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setProteinGrams(proteinGrams);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getCarbsGrams() {
        return nutritionFactsSnapshot().getCarbsGrams();
    }

    default void setCarbsGrams(BigDecimal carbsGrams) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setCarbsGrams(carbsGrams);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getFatGrams() {
        return nutritionFactsSnapshot().getFatGrams();
    }

    default void setFatGrams(BigDecimal fatGrams) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setFatGrams(fatGrams);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getFiberGrams() {
        return nutritionFactsSnapshot().getFiberGrams();
    }

    default void setFiberGrams(BigDecimal fiberGrams) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setFiberGrams(fiberGrams);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getSugarGrams() {
        return nutritionFactsSnapshot().getSugarGrams();
    }

    default void setSugarGrams(BigDecimal sugarGrams) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setSugarGrams(sugarGrams);
        replaceNutritionFacts(facts);
    }

    default BigDecimal getSodiumMg() {
        return nutritionFactsSnapshot().getSodiumMg();
    }

    default void setSodiumMg(BigDecimal sodiumMg) {
        NutritionFacts facts = nutritionFactsSnapshot();
        facts.setSodiumMg(sodiumMg);
        replaceNutritionFacts(facts);
    }
}
