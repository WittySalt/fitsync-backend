package com.fitsync.recommendation.coach.tool;

/** Nutrition-service bodies in the shape of the diary contract, shared by the tool tests. */
final class DiaryFixtures {

    static final String SUMMARY = """
            {"totalCalories":1200,"totalProteinGrams":78.25,
             "totalSaturatedFatGrams":14,"totalPotassiumMg":2100,
             "waterMl":1200,"waterFromDrinksMl":350,"sugarFromDrinksGrams":18.04,
             "balance":[
               {"code":"FIBER","displayName":"Fibre","kind":"TARGET","amount":22.4,"unit":"g","reference":30,"share":0.746,"met":false,"over":false},
               {"code":"POTASSIUM","displayName":"Potassium","kind":"TARGET","amount":2100,"unit":"mg","reference":3500,"share":0.6,"met":false,"over":false},
               {"code":"SUGAR","displayName":"Sugar","kind":"LIMIT","amount":62,"unit":"g","reference":50,"share":1.24,"met":false,"over":true},
               {"code":"SAT_FAT","displayName":"Saturated fat","kind":"LIMIT","amount":14,"unit":"g","reference":20,"share":0.7,"met":false,"over":false},
               {"code":"SODIUM","displayName":"Sodium","kind":"LIMIT","amount":1800,"unit":"mg","reference":2300,"share":0.78,"met":false,"over":false}],
             "proteinByMeal":{"weightKg":70,"lowGrams":21,"highGrams":28,"meals":[
               {"mealType":"BREAKFAST","grams":30},{"mealType":"LUNCH","grams":48.25},
               {"mealType":"DINNER","grams":0},{"mealType":"SNACK","grams":0}]}}""";

    static final String NUTRIENTS = """
            {"from":"2026-10-01","to":"2026-10-01","days":1,"daysLogged":1,
             "coverage":{"itemsLogged":5,"itemsWithoutNutrients":2},
             "nutrients":[
               {"code":"VIT_B12","displayName":"Vitamin B12","kind":"VITAMIN","unit":"mcg","averageAmount":1.2,"reference":2.4,"share":0.5,"met":false,"upperLimit":null,"aboveUpperLimit":false},
               {"code":"VIT_D","displayName":"Vitamin D","kind":"VITAMIN","unit":"mcg","averageAmount":1.8,"reference":15,"share":0.12,"met":false,"upperLimit":100,"aboveUpperLimit":false},
               {"code":"IRON","displayName":"Iron","kind":"MINERAL","unit":"mg","averageAmount":6,"reference":8,"share":0.75,"met":false,"upperLimit":45,"aboveUpperLimit":false},
               {"code":"VIT_A","displayName":"Vitamin A","kind":"VITAMIN","unit":"mcg","averageAmount":1200,"reference":900,"share":1.33,"met":true,"upperLimit":900,"aboveUpperLimit":true},
               {"code":"CAFFEINE","displayName":"Caffeine","kind":"OTHER","unit":"mg","averageAmount":0,"reference":1,"share":0,"met":false,"upperLimit":null,"aboveUpperLimit":false}]}""";

    private DiaryFixtures() {
    }
}
