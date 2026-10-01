package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.nutrition.client.PersonFacts;
import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The person's sex, birth date, weight and dietary pattern arrive as three kinds of events and meet in one row.
 * These tests feed the consumers directly and read the row back through the lookup the Diary uses.
 */
@SpringBootTest
@ActiveProfiles("test")
class PersonFactsProjectionTests {

    @Autowired
    private DomainEventConsumer<UserProfileUpdatedPayload> profileConsumer;

    @Autowired
    private DomainEventConsumer<UserPreferencesUpdatedPayload> preferencesConsumer;

    @Autowired
    private DomainEventConsumer<BodyMetricsRecordedPayload> bodyMetricsConsumer;

    @Autowired
    private NutritionPreferenceProjectionRepository repository;

    @Autowired
    private PersonLookup lookup;

    @Autowired
    private Clock clock;

    private static <T> FitSyncDomainEvent<T> event(String type, UUID userId, T payload) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), type, 1, Instant.parse("2026-10-02T00:00:00Z"),
                "user-service", userId, payload);
    }

    private void profile(UUID userId, String gender, LocalDate birthDate) {
        profileConsumer.consume(event("UserProfileUpdated", userId, new UserProfileUpdatedPayload(
                "Alice", new BigDecimal("170.0"), "MODERATELY_ACTIVE", true, List.of("gender"), "Asia/Singapore",
                gender, birthDate)));
    }

    private void weight(UUID userId, String kg, String measuredAt) {
        bodyMetricsConsumer.consume(event("BodyMetricsRecorded", userId, new BodyMetricsRecordedPayload(
                UUID.randomUUID(), kg == null ? null : new BigDecimal(kg), null, null, Instant.parse(measuredAt))));
    }

    @Test
    void aPersonWithNoRowIsUnknown() {
        PersonFacts facts = lookup.facts(UUID.randomUUID());

        assertThat(facts.basics().sex()).isNull();
        assertThat(facts.basics().age()).isNull();
        assertThat(facts.weightKg()).isNull();
        assertThat(facts.dietaryPattern()).isNull();
    }

    @Test
    void profileWeightAndPreferencesFillOneRowThatTheLookupReads() {
        UUID userId = UUID.randomUUID();
        LocalDate birthDate = LocalDate.now(clock).minusYears(34).minusDays(3);

        profile(userId, "FEMALE", birthDate);
        weight(userId, "61.50", "2026-10-01T08:00:00Z");
        preferencesConsumer.consume(event("UserPreferencesUpdated", userId,
                new UserPreferencesUpdatedPayload("100001", 4, "300003", true, List.of(), List.of())));

        PersonFacts facts = lookup.facts(userId);
        assertThat(facts.basics().sex()).isEqualTo("FEMALE");
        assertThat(facts.basics().age()).isEqualTo(34);
        assertThat(facts.weightKg()).isEqualByComparingTo("61.5");
        assertThat(facts.dietaryPattern()).isEqualTo("VEGETARIAN");
        assertThat(repository.findById(userId)).isPresent();
    }

    @Test
    void anotherEventKindDoesNotWipeTheOtherFacts() {
        UUID userId = UUID.randomUUID();
        profile(userId, "MALE", LocalDate.parse("1990-01-01"));
        weight(userId, "80.0", "2026-10-01T08:00:00Z");

        preferencesConsumer.consume(event("UserPreferencesUpdated", userId,
                new UserPreferencesUpdatedPayload("100001", 4, "300004", true, List.of(), List.of())));
        profile(userId, "MALE", LocalDate.parse("1990-01-01"));

        PersonFacts facts = lookup.facts(userId);
        assertThat(facts.weightKg()).isEqualByComparingTo("80.0");
        assertThat(facts.dietaryPattern()).isEqualTo("VEGAN");
        assertThat(facts.basics().sex()).isEqualTo("MALE");
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
    void sexOtherThanMaleOrFemaleAndAnImpossibleBirthDateStayUnknown() {
        UUID userId = UUID.randomUUID();

        profile(userId, "PREFER_NOT_TO_SAY", LocalDate.now(clock).plusYears(2));

        PersonFacts facts = lookup.facts(userId);
        assertThat(facts.basics().sex()).isNull();
        assertThat(facts.basics().age()).isNull();
    }

    @Test
    void aProfileEventThatClearsTheBirthDateClearsIt() {
        UUID userId = UUID.randomUUID();
        profile(userId, "FEMALE", LocalDate.parse("1990-01-01"));

        profile(userId, "FEMALE", null);

        assertThat(lookup.facts(userId).basics().age()).isNull();
        assertThat(lookup.facts(userId).basics().sex()).isEqualTo("FEMALE");
    }
}
