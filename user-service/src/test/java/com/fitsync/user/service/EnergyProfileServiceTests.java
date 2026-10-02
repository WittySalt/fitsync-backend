package com.fitsync.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import com.fitsync.user.messaging.DomainEventPublisher;
import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;
import com.fitsync.user.entity.BodyMetricEntryEntity;
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.repository.BodyMetricEntryRepository;
import com.fitsync.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnergyProfileServiceTests {

    // Fixed "now": 2026-07-06. Born 1996-07-01 -> age 30 exactly at compute time.
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final UserProfileRepository profileRepository = mock(UserProfileRepository.class);
    private final BodyMetricEntryRepository metricRepository = mock(BodyMetricEntryRepository.class);
    private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
    private final EnergyProfileService service =
            new EnergyProfileService(profileRepository, metricRepository, publisher, clock);

    private final UUID userId = UUID.randomUUID();

    @Test
    void computesMifflinStJeorWithRealAgeAndSexAndPublishesOnlyTheResults() {
        givenProfile(profile(Gender.MALE, LocalDate.parse("1996-07-01"), "181.5", ActivityLevel.MODERATELY_ACTIVE));
        givenLatestWeight("82.50");

        service.publishEnergyProfile(userId);

        // BMR = 10*82.5 + 6.25*181.5 - 5*30 + 5 = 1814.375 -> 1814; TDEE = *1.55 = 2812.28 -> 2812.
        EnergyProfileComputedPayload payload = publishedPayload();
        assertThat(payload.bmrCalories()).isEqualTo(1814);
        assertThat(payload.tdeeCalories()).isEqualTo(2812);
        assertThat(payload.activityLevel()).isEqualTo("MODERATELY_ACTIVE");
        assertThat(payload.computedAt()).isEqualTo(Instant.parse("2026-07-06T12:00:00Z"));
        assertThat(payload.estimated()).isFalse();
    }

    @Test
    void fallsBackToNeutralConstantsWithoutBirthDateOrDisclosedSex() {
        givenProfile(profile(Gender.PREFER_NOT_TO_SAY, null, "181.5", null));
        givenLatestWeight("82.50");

        service.publishEnergyProfile(userId);

        // BMR = 10*82.5 + 6.25*181.5 - 5*30 - 78 = 1731.375 -> 1731; TDEE = *1.4 = 2423.9 -> 2424.
        EnergyProfileComputedPayload payload = publishedPayload();
        assertThat(payload.bmrCalories()).isEqualTo(1731);
        assertThat(payload.tdeeCalories()).isEqualTo(2424);
        assertThat(payload.activityLevel()).isNull();
        assertThat(payload.estimated()).isTrue();
    }

    @Test
    void staysSilentWhileHeightOrWeightIsUnknown() {
        givenProfile(profile(Gender.FEMALE, LocalDate.parse("1990-01-01"), null, ActivityLevel.SEDENTARY));
        givenLatestWeight("60.00");
        service.publishEnergyProfile(userId);

        givenProfile(profile(Gender.FEMALE, LocalDate.parse("1990-01-01"), "165.0", ActivityLevel.SEDENTARY));
        when(metricRepository.findTopByUserIdOrderByMeasuredAtDesc(userId)).thenReturn(Optional.empty());
        service.publishEnergyProfile(userId);

        verify(publisher, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void flagsEstimatedWhenOnlyAgeOrOnlySexFellBackAndReportsPublishing() {
        givenLatestWeight("82.50");

        givenProfile(profile(Gender.MALE, null, "181.5", null));
        assertThat(service.publishEnergyProfile(userId)).isTrue();
        assertThat(publishedPayload().estimated()).isTrue();

        org.mockito.Mockito.clearInvocations(publisher);
        givenProfile(profile(Gender.NON_BINARY, LocalDate.parse("1996-07-01"), "181.5", null));
        service.publishEnergyProfile(userId);
        assertThat(publishedPayload().estimated()).isTrue();

        givenProfile(profile(Gender.FEMALE, LocalDate.parse("1990-01-01"), null, null));
        org.mockito.Mockito.clearInvocations(publisher);
        assertThat(service.publishEnergyProfile(userId)).isFalse();
    }

    private void givenProfile(UserProfileEntity profile) {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    }

    private void givenLatestWeight(String weightKg) {
        BodyMetricEntryEntity metric = new BodyMetricEntryEntity();
        metric.setUserId(userId);
        metric.setWeightKg(new BigDecimal(weightKg));
        metric.setMeasuredAt(Instant.parse("2026-07-06T07:00:00Z"));
        when(metricRepository.findTopByUserIdOrderByMeasuredAtDesc(userId)).thenReturn(Optional.of(metric));
    }

    private UserProfileEntity profile(Gender gender, LocalDate birthDate, String heightCm, ActivityLevel activity) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setDisplayName("Verify User");
        profile.setGender(gender);
        profile.setBirthDate(birthDate);
        profile.setHeightCm(heightCm == null ? null : new BigDecimal(heightCm));
        profile.setActivityLevel(activity);
        return profile;
    }

    private EnergyProfileComputedPayload publishedPayload() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<FitSyncDomainEvent> captor = ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(publisher).publish(eq("user-events"), eq("UserProfile"), eq(userId.toString()), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("EnergyProfileComputed");
        return (EnergyProfileComputedPayload) captor.getValue().payload();
    }
}

