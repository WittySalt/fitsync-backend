package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.fitsync.nutrition.client.PersonBasics;
import com.fitsync.nutrition.entity.NutrientDailyReferenceEntity;
import com.fitsync.nutrition.repository.NutrientDailyReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the daily reference amounts that apply to a person from nutrient_daily_references. A row for the
 * person's own sex wins over the ANY row for the same nutrient and kind. A person whose sex or age is not
 * known gets the ANY rows for an adult of 19 to 50, and an age below 19 is read as 19.
 */
@Service
public class NutrientReferenceService {

    static final String KIND_TARGET = "TARGET";
    static final String KIND_LIMIT = "LIMIT";
    static final String KIND_UPPER = "UPPER";

    private static final String ANY = "ANY";
    private static final int DEFAULT_ADULT_AGE = 35;
    private static final int MIN_ADULT_AGE = 19;
    private static final int OPEN_ENDED_AGE = 120;

    private final NutrientDailyReferenceRepository referenceRepository;

    public NutrientReferenceService(NutrientDailyReferenceRepository referenceRepository) {
        this.referenceRepository = referenceRepository;
    }

    @Transactional(readOnly = true)
    public PersonReferences forPerson(PersonBasics person) {
        boolean sexKnown = person.sex() != null;
        String sexKey = sexKnown ? person.sex() : ANY;
        int age = person.age() == null ? DEFAULT_ADULT_AGE : Math.min(Math.max(person.age(), MIN_ADULT_AGE), OPEN_ENDED_AGE);

        Map<String, NutrientDailyReferenceEntity> best = new HashMap<>();
        NutrientDailyReferenceEntity bandRow = null;
        for (NutrientDailyReferenceEntity row : referenceRepository.findApplicable(sexKey, age)) {
            String key = row.getNutrientCode() + "|" + row.getKind();
            NutrientDailyReferenceEntity current = best.get(key);
            if (current == null || (ANY.equals(current.getSex()) && !ANY.equals(row.getSex()))) {
                best.put(key, row);
            }
            if (bandRow == null && !ANY.equals(row.getSex())) {
                bandRow = row;
            }
        }
        boolean described = sexKnown && person.age() != null && bandRow != null;
        return new PersonReferences(best, described ? label(sexKey, bandRow) : "for an adult");
    }

    private String label(String sex, NutrientDailyReferenceEntity bandRow) {
        String who = "MALE".equals(sex) ? "a man" : "a woman";
        if (bandRow.getAgeTo() >= OPEN_ENDED_AGE) {
            return "for " + who + " aged " + bandRow.getAgeFrom() + " and over";
        }
        return "for " + who + " aged " + bandRow.getAgeFrom() + " to " + bandRow.getAgeTo();
    }

    /** The amounts that apply to one person, looked up by nutrient code. */
    public static final class PersonReferences {

        private final Map<String, NutrientDailyReferenceEntity> rows;
        private final String label;

        private PersonReferences(Map<String, NutrientDailyReferenceEntity> rows, String label) {
            this.rows = Map.copyOf(rows);
            this.label = label;
        }

        /** The amount to reach, or null when the nutrient has none. */
        public BigDecimal target(String nutrientCode) {
            return amount(nutrientCode, KIND_TARGET);
        }

        /** The amount not to pass, or null when the nutrient has none. */
        public BigDecimal limit(String nutrientCode) {
            return amount(nutrientCode, KIND_LIMIT);
        }

        /** The tolerable upper intake, or null when the nutrient has none. */
        public BigDecimal upper(String nutrientCode) {
            return amount(nutrientCode, KIND_UPPER);
        }

        /** For example "for a man aged 19 to 30", or "for an adult" when sex or age is unknown. */
        public String label() {
            return label;
        }

        private BigDecimal amount(String nutrientCode, String kind) {
            NutrientDailyReferenceEntity row = rows.get(nutrientCode + "|" + kind);
            return row == null ? null : row.getAmount();
        }
    }
}
