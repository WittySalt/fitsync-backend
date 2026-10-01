package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.NutritionReferenceProfileUpdatedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.nutrition.client.PersonFacts;
import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The person's reference sex, age band, weight and dietary pattern arrive as three kinds of events and meet in one row.
 * These tests feed the consumers directly and read the row back through the lookup the Diary uses.
 */
@SpringBootTest
@ActiveProfiles("test")
class PersonFactsProjectionTests {

    @Autowired
    private DomainEventConsumer<NutritionReferenceProfileUpdatedPayload> profileConsumer;

    @Autowired
    private DomainEventConsumer<UserPreferencesUpdatedPayload> preferencesConsumer;

    @Autowired
    private DomainEventConsumer<BodyMetricsRecordedPayload> bodyMetricsConsumer;

    @Autowired
    private NutritionPreferenceProjectionRepository repository;

    @Autowired
    private PersonLookup lookup;

    private static <T> FitSyncDomainEvent<T> event(String type, UUID userId, T payload) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), type, 1, Instant.parse("2026-10-02T00:00:00Z"),
                "user-service", userId, payload);
    }

    private void profile(UUID userId, String referenceSex, String ageBand) {
        profileConsumer.consume(event("NutritionReferenceProfileUpdated", userId,
                new NutritionReferenceProfileUpdatedPayload(referenceSex, ageBand)));
    }

    private void weight(UUID userId, String kg, String measuredAt) {
        bodyMetricsConsumer.consume(event("BodyMetricsRecorded", userId, new BodyMetricsRecordedPayload(
                UUID.randomUUID(), kg == null ? null : new BigDecimal(kg), null, null, Instant.parse(measuredAt))));
    }

    @Test
    void aPersonWithNoRowIsUnknown() {
        PersonFacts facts = lookup.facts(UUID.randomUUID());

        assertThat(facts.basics().referenceSex()).isNull();
        assertThat(facts.basics().age()).isNull();
        assertThat(facts.weightKg()).isNull();
        assertThat(facts.dietaryPattern()).isNull();
    }

    @Test
    void profileWeightAndPreferencesFillOneRowThatTheLookupReads() {
        UUID userId = UUID.randomUUID();

        profile(userId, "FEMALE", "31-50");
        weight(userId, "61.50", "2026-10-01T08:00:00Z");
        preferencesConsumer.consume(event("UserPreferencesUpdated", userId,
                new UserPreferencesUpdatedPayload("100001", 4, "300003", true, List.of(), List.of())));

        PersonFacts facts = lookup.facts(userId);
        assertThat(facts.basics().referenceSex()).isEqualTo("FEMALE");
        assertThat(facts.basics().age()).isEqualTo(31);
        assertThat(facts.weightKg()).isEqualByComparingTo("61.5");
        assertThat(facts.dietaryPattern()).isEqualTo("VEGETARIAN");
        assertThat(repository.findById(userId)).isPresent();
    }

    @Test
    void anotherEventKindDoesNotWipeTheOtherFacts() {
        UUID userId = UUID.randomUUID();
        profile(userId, "MALE", "31-50");
        weight(userId, "80.0", "2026-10-01T08:00:00Z");

        preferencesConsumer.consume(event("UserPreferencesUpdated", userId,
                new UserPreferencesUpdatedPayload("100001", 4, "300004", true, List.of(), List.of())));
        profile(userId, "MALE", "31-50");

        PersonFacts facts = lookup.facts(userId);
        assertThat(facts.weightKg()).isEqualByComparingTo("80.0");
        assertThat(facts.dietaryPattern()).isEqualTo("VEGAN");
        assertThat(facts.basics().referenceSex()).isEqualTo("MALE");
    }

    @Test
    void onlyTheNewestWeighInIsKept() {
        UUID userId = UUID.randomUUID();

        weight(userId, "80.0", "2026-10-02T08:00:00Z");
        weight(userId, "85.0", "2026-09-01T08:00:00Z");
        assertThat(lookup.facts(userId).weightKg()).isEqualByComparingTo("80.0");

        weight(userId, "79.0", "2026-10-03T08:00:00Z");
        assertThat(lookup.facts(userId).weightKg()).isEqualByComparingTo("79.0");
    }

    @Test
    void anEventWithNoWeightIsIgnoredAndReplayingAnEventChangesNothing() {
        UUID userId = UUID.randomUUID();
        weight(userId, "70.0", "2026-10-01T08:00:00Z");

        weight(userId, null, "2026-10-02T08:00:00Z");
        weight(userId, "70.0", "2026-10-01T08:00:00Z");

        assertThat(lookup.facts(userId).weightKg()).isEqualByComparingTo("70.0");
        assertThat(repository.findById(userId).orElseThrow().getWeightMeasuredAt())
                .isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    void blendedIsKeptAndAnUnknownSexOrBandStaysUnknown() {
        UUID blended = UUID.randomUUID();
        profile(blended, "BLENDED", "71+");
        assertThat(lookup.facts(blended).basics().referenceSex()).isEqualTo("BLENDED");
        assertThat(lookup.facts(blended).basics().age()).isEqualTo(71);

        UUID odd = UUID.randomUUID();
        profile(odd, "SOMETHING_ELSE", "9-12");
        assertThat(lookup.facts(odd).basics().referenceSex()).isNull();
        assertThat(lookup.facts(odd).basics().age()).isNull();
    }

    @Test
    void aProfileEventWithoutAnAgeBandClearsItAndReplayingChangesNothing() {
        UUID userId = UUID.randomUUID();
        profile(userId, "FEMALE", "19-30");
        assertThat(lookup.facts(userId).basics().age()).isEqualTo(19);

        profile(userId, "FEMALE", null);
        profile(userId, "FEMALE", null);

        assertThat(lookup.facts(userId).basics().age()).isNull();
        assertThat(lookup.facts(userId).basics().referenceSex()).isEqualTo("FEMALE");
    }

    @Test
    void everyBandStartsAtItsYoungestAge() {
        String[] bands = {"19-30", "31-50", "51-70", "71+"};
        int[] ages = {19, 31, 51, 71};
        for (int i = 0; i < bands.length; i++) {
            UUID userId = UUID.randomUUID();
            profile(userId, "MALE", bands[i]);
            assertThat(lookup.facts(userId).basics().age()).isEqualTo(ages[i]);
        }
    }
}
