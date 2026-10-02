package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.NutrientDailyReferenceEntity;
import com.fitsync.nutrition.entity.NutrientEntity;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.NutrientDailyReferenceRepository;
import com.fitsync.nutrition.repository.NutrientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NutritionReferenceDataTests {

    @Autowired
    private NutrientRepository nutrientRepository;

    @Autowired
    private NutrientDailyReferenceRepository referenceRepository;

    @Autowired
    private FoodCategoryRepository foodCategoryRepository;

    @Test
    void nutrientDictionaryHasEveryTrackedNutrientWithItsUsdaNumber() {
        Map<String, NutrientEntity> byCode = nutrientRepository.findAll().stream()
                .collect(Collectors.toMap(NutrientEntity::getCode, Function.identity()));

        assertThat(byCode).hasSize(26);
        assertThat(byCode.get("VIT_C").getUsdaNutrientNumber()).isEqualTo(401);
        assertThat(byCode.get("CALCIUM").getUsdaNutrientNumber()).isEqualTo(301);
        assertThat(byCode.get("VIT_D").getUnit()).isEqualTo("µg");
        assertThat(byCode.values().stream().filter(NutrientEntity::isShownInDiary).map(NutrientEntity::getCode))
                .containsExactlyInAnyOrder("VIT_D", "VIT_A", "VIT_E", "VIT_C", "CALCIUM", "MAGNESIUM", "IRON");
    }

    @Test
    void referencesAreSpecificToSexAndAgeWithAnAnyFallback() {
        assertThat(amount("VIT_C", "MALE", 25, "TARGET")).isEqualByComparingTo("90");
        assertThat(amount("VIT_C", "FEMALE", 25, "TARGET")).isEqualByComparingTo("75");
        assertThat(amount("IRON", "FEMALE", 35, "TARGET")).isEqualByComparingTo("18");
        assertThat(amount("IRON", "FEMALE", 60, "TARGET")).isEqualByComparingTo("8");
        assertThat(amount("VIT_D", "MALE", 75, "TARGET")).isEqualByComparingTo("20");
        assertThat(amount("CALCIUM", "ANY", 40, "UPPER")).isEqualByComparingTo("2500");
        assertThat(amount("CALCIUM", "ANY", 60, "UPPER")).isEqualByComparingTo("2000");
        assertThat(amount("POTASSIUM", "MALE", 30, "TARGET")).isEqualByComparingTo("3400");
        assertThat(amount("POTASSIUM", "FEMALE", 30, "TARGET")).isEqualByComparingTo("2600");
        assertThat(amount("POTASSIUM", "ANY", 30, "TARGET")).isEqualByComparingTo("3000");
        assertThat(amount("FIBER", "ANY", 30, "TARGET")).isEqualByComparingTo("28");
        assertThat(amount("SUGAR", "ANY", 30, "LIMIT")).isEqualByComparingTo("50");
        assertThat(amount("SAT_FAT", "ANY", 30, "LIMIT")).isEqualByComparingTo("22");
        assertThat(amount("SODIUM", "ANY", 30, "LIMIT")).isEqualByComparingTo("2300");
    }

    @Test
    void potassiumHasNoUpperLimit() {
        assertThat(referenceRepository.findApplicable("MALE", 30)).noneMatch(
                r -> r.getNutrientCode().equals("POTASSIUM") && r.getKind().equals("UPPER"));
    }

    @Test
    void everyReferenceNamesItsSource() {
        assertThat(referenceRepository.findAll())
                .isNotEmpty()
                .allMatch(r -> "NIH Dietary Reference Intakes".equals(r.getSource()));
    }

    @Test
    void foodCategoriesMapToTheirPlateGroup() {
        Map<String, String> groups = foodCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(FoodCategoryEntity::getCode, FoodCategoryEntity::getFoodGroup));

        assertThat(groups).containsEntry("VEGETABLES", "VEG")
                .containsEntry("FRUITS", "FRUIT")
                .containsEntry("GRAINS", "GRAINS")
                .containsEntry("MEAT_POULTRY", "PROTEIN")
                .containsEntry("SEAFOOD", "PROTEIN")
                .containsEntry("EGGS", "PROTEIN")
                .containsEntry("PLANT_PROTEIN", "PROTEIN")
                .containsEntry("PROTEIN_SUPPLEMENT", "PROTEIN")
                .containsEntry("DAIRY", "DAIRY")
                .containsEntry("NUTS_SEEDS", "NUTS")
                .containsEntry("FATS_OILS", "NUTS")
                .containsEntry("LEGUMES", "LEGUMES");
    }

    private BigDecimal amount(String code, String sex, int age, String kind) {
        List<NutrientDailyReferenceEntity> rows = referenceRepository.findApplicable(sex, age);
        return rows.stream()
                .filter(r -> r.getNutrientCode().equals(code) && r.getKind().equals(kind) && r.getSex().equals(sex))
                .map(NutrientDailyReferenceEntity::getAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + kind + " row for " + code + " " + sex + " " + age));
    }
}
