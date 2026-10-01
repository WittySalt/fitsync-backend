package com.fitsync.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.NutrientReferenceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NutritionReferenceProfileDeriverTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource({
            "MALE,PROFILE,MALE",
            "FEMALE,PROFILE,FEMALE",
            "NON_BINARY,PROFILE,BLENDED",
            "PREFER_NOT_TO_SAY,PROFILE,BLENDED",
            "NONE,PROFILE,BLENDED",
            "MALE,FEMALE,FEMALE",
            "FEMALE,MALE,MALE",
            "FEMALE,BLENDED,BLENDED",
            "NONE,MALE,MALE"
    })
    void derivesReferenceSexFromPreferenceThenGender(String gender, String preference, String expected) {
        Gender parsed = "NONE".equals(gender) ? null : Gender.valueOf(gender);
        assertEquals(expected,
                NutritionReferenceProfileDeriver.referenceSex(parsed, NutrientReferenceRange.valueOf(preference)));
    }

    @Test
    void missingPreferenceFollowsTheProfile() {
        assertEquals("FEMALE", NutritionReferenceProfileDeriver.referenceSex(Gender.FEMALE, null));
    }

    @ParameterizedTest
    @CsvSource({
            "2015-01-01,19-30",
            "2007-10-03,19-30",
            "2000-10-02,19-30",
            "1995-10-02,31-50",
            "1995-10-03,19-30",
            "1976-10-03,31-50",
            "1976-10-02,31-50",
            "1975-10-03,31-50",
            "1975-10-02,51-70",
            "1956-10-03,51-70",
            "1955-10-02,71+",
            "1930-01-01,71+"
    })
    void derivesAgeBandAtTheBoundaries(String birthDate, String expected) {
        assertEquals(expected, NutritionReferenceProfileDeriver.ageBand(LocalDate.parse(birthDate), CLOCK));
    }

    @Test
    void noBirthDateGivesNoAgeBand() {
        assertNull(NutritionReferenceProfileDeriver.ageBand(null, CLOCK));
    }

    @Test
    void deriveCombinesBothValues() {
        NutritionReferenceProfileDeriver.Derived derived = NutritionReferenceProfileDeriver.derive(
                Gender.NON_BINARY, LocalDate.parse("1980-01-01"), NutrientReferenceRange.PROFILE, CLOCK);
        assertEquals("BLENDED", derived.referenceSex());
        assertEquals("31-50", derived.ageBand());
    }
}
