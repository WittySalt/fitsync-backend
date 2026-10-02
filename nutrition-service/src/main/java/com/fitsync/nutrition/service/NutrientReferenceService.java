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
 * person's own reference sex wins over the ANY row for the same nutrient and kind, and a BLENDED person reads
 * the ANY rows. A person whose sex or age band is not known gets the ANY rows for an adult of 19 to 50, and
 * an age below 19 is read as 19.
 */
@Service
public class NutrientReferenceService {

    static final String KIND_TARGET = "TARGET";
    static final String KIND_LIMIT = "LIMIT";
    static final String KIND_UPPER = "UPPER";

    private static final String ANY = "ANY";
    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    private static final String BLENDED = "BLENDED";
    private static final String NOTHING_KNOWN_LABEL = "for an adult";
    private static final int DEFAULT_ADULT_AGE = 35;
    private static final int MIN_ADULT_AGE = 19;
    private static final int OPEN_ENDED_AGE = 120;

    private final NutrientDailyReferenceRepository referenceRepository;

    public NutrientReferenceService(NutrientDailyReferenceRepository referenceRepository) {
        this.referenceRepository = referenceRepository;
    }

    @Transactional(readOnly = true)
    public PersonReferences forPerson(PersonBasics person) {
        String range = MALE.equals(person.referenceSex()) || FEMALE.equals(person.referenceSex())
                || BLENDED.equals(person.referenceSex()) ? person.referenceSex() : null;
        String sexKey = MALE.equals(range) || FEMALE.equals(range) ? range : ANY;
        boolean ageKnown = person.age() != null;
        int age = ageKnown ? Math.min(Math.max(person.age(), MIN_ADULT_AGE), OPEN_ENDED_AGE) : DEFAULT_ADULT_AGE;

        Map<String, NutrientDailyReferenceEntity> best = new HashMap<>();
        NutrientDailyReferenceEntity bandRow = null;
        for (NutrientDailyReferenceEntity row : referenceRepository.findApplicable(sexKey, age)) {
            String key = row.getNutrientCode() + "|" + row.getKind();
            NutrientDailyReferenceEntity current = best.get(key);
            if (current == null || (ANY.equals(current.getSex()) && !ANY.equals(row.getSex()))) {
                best.put(key, row);
            }
            if (bandRow == null) {
                bandRow = row;
            }
        }
        return new PersonReferences(best, range, label(range, ageKnown ? bandRow : null));
    }

    private String label(String range, NutrientDailyReferenceEntity bandRow) {
        if (range == null && bandRow == null) {
            return NOTHING_KNOWN_LABEL;
        }
        StringBuilder text = new StringBuilder();
        if (range == null) {
            text.append(NOTHING_KNOWN_LABEL);
        } else if (BLENDED.equals(range)) {
            text.append("for blended adult ranges");
        } else {
            text.append(MALE.equals(range) ? "for male ranges" : "for female ranges");
        }
        if (bandRow != null) {
            text.append(range == null ? " aged " : ", aged ");
            if (bandRow.getAgeTo() >= OPEN_ENDED_AGE) {
                text.append(bandRow.getAgeFrom()).append(" and over");
            } else {
                text.append(bandRow.getAgeFrom()).append(" to ").append(bandRow.getAgeTo());
            }
        }
        return text.toString();
    }

    /** The amounts that apply to one person, looked up by nutrient code. */
    public static final class PersonReferences {

        private final Map<String, NutrientDailyReferenceEntity> rows;
        private final String range;
        private final String label;

        private PersonReferences(Map<String, NutrientDailyReferenceEntity> rows, String range, String label) {
            this.rows = Map.copyOf(rows);
            this.range = range;
            this.label = label;
        }

        /** FEMALE, MALE or BLENDED, or null when nothing is known about the person. */
        public String range() {
            return range;
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

        /** For example "for female ranges, aged 19 to 30", or "for an adult" when nothing is known. */
        public String label() {
            return label;
        }

        private BigDecimal amount(String nutrientCode, String kind) {
            NutrientDailyReferenceEntity row = rows.get(nutrientCode + "|" + kind);
            return row == null ? null : row.getAmount();
        }
    }
}
