package com.fitsync.nutrition.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the person's reference sex, age band, latest weight and dietary pattern from the preference projection
 * that the NutritionReferenceProfileUpdated, UserPreferencesUpdated and BodyMetricsRecorded events fill. There is no call to another
 * service, so nothing here can be slow or fail because user-service is down. A person with no row yet is
 * unknown, and the references then fall back to an adult.
 */
@Component
public class ProjectionPersonLookup implements PersonLookup {

    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    private static final String BLENDED = "BLENDED";
    // The youngest age of each age band user-service publishes, which is what the reference rows are looked up by.
    private static final Map<String, Integer> BAND_START_AGES = Map.of("19-30", 19, "31-50", 31, "51-70", 51, "71+", 71);
    // user-service writes the numeric code of a dietary pattern. The names and the old lower-case codes are
    // accepted as well, so that a change in how it writes the value does not silently drop the focus nutrients.
    private static final Map<String, String> DIETARY_PATTERNS = Map.ofEntries(
            Map.entry("300001", "NONE"), Map.entry("300002", "HALAL"), Map.entry("300003", "VEGETARIAN"),
            Map.entry("300004", "VEGAN"), Map.entry("300005", "KETO"), Map.entry("300006", "LOW_CARB"),
            Map.entry("NONE", "NONE"), Map.entry("HALAL", "HALAL"), Map.entry("VEGETARIAN", "VEGETARIAN"),
            Map.entry("VEGAN", "VEGAN"), Map.entry("KETO", "KETO"), Map.entry("LOW_CARB", "LOW_CARB"),
            Map.entry("none", "NONE"), Map.entry("halal", "HALAL"), Map.entry("vegetarian", "VEGETARIAN"),
            Map.entry("vegan", "VEGAN"), Map.entry("keto", "KETO"), Map.entry("low_carb", "LOW_CARB"));

    private final NutritionPreferenceProjectionRepository repository;

    public ProjectionPersonLookup(NutritionPreferenceProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PersonFacts facts(UUID userId) {
        if (userId == null) {
            return PersonFacts.unknown();
        }
        NutritionPreferenceProjectionEntity row = repository.findById(userId).orElse(null);
        if (row == null) {
            return PersonFacts.unknown();
        }
        BigDecimal weight = row.getWeightKg() != null && row.getWeightKg().signum() > 0 ? row.getWeightKg() : null;
        String diet = row.getDietaryPreferenceCode() == null ? null : DIETARY_PATTERNS.get(row.getDietaryPreferenceCode());
        return new PersonFacts(new PersonBasics(sexOf(row.getReferenceSex()), ageOf(row.getAgeBand())), weight, diet);
    }

    private String sexOf(String referenceSex) {
        if (MALE.equals(referenceSex) || FEMALE.equals(referenceSex) || BLENDED.equals(referenceSex)) {
            return referenceSex;
        }
        return null;
    }

    private Integer ageOf(String ageBand) {
        return ageBand == null ? null : BAND_START_AGES.get(ageBand);
    }
}
