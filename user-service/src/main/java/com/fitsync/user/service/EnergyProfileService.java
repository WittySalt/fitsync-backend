package com.fitsync.user.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * EV-3 — computes the user's energy profile (Mifflin–St Jeor BMR × activity factor)
 * and publishes ONLY the results as {@code EnergyProfileComputed}. The PII inputs
 * (birth date, gender) never leave this service; downstream consumers get precise
 * numbers instead of re-estimating with neutral constants.
 *
 * Published whenever a relevant input may have changed (profile upsert, body-metric
 * writes); silently skipped while the inputs are incomplete (no height or weight yet).
 */
@Service
public class EnergyProfileService {

    static final int DEFAULT_AGE_YEARS = 30;
    static final int SEX_CONSTANT_MALE = 5;
    static final int SEX_CONSTANT_FEMALE = -161;
    /** Midpoint of the Mifflin–St Jeor sex constants, for non-binary/undisclosed. */
    static final int SEX_CONSTANT_NEUTRAL = -78;

    private static final String USER_EVENTS_TOPIC = "user-events";
    private static final String SOURCE = "user-service";
    private static final double DEFAULT_ACTIVITY_FACTOR = 1.4;
    private static final Map<ActivityLevel, Double> ACTIVITY_FACTORS = Map.of(
            ActivityLevel.SEDENTARY, 1.2,
            ActivityLevel.LIGHTLY_ACTIVE, 1.375,
            ActivityLevel.MODERATELY_ACTIVE, 1.55,
            ActivityLevel.VERY_ACTIVE, 1.725,
            ActivityLevel.ATHLETE, 1.9);

    private final UserProfileRepository userProfileRepository;
    private final BodyMetricEntryRepository bodyMetricEntryRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    @Autowired
    public EnergyProfileService(UserProfileRepository userProfileRepository,
                                BodyMetricEntryRepository bodyMetricEntryRepository,
                                DomainEventPublisher domainEventPublisher) {
        this(userProfileRepository, bodyMetricEntryRepository, domainEventPublisher, Clock.systemUTC());
    }

    EnergyProfileService(UserProfileRepository userProfileRepository,
                         BodyMetricEntryRepository bodyMetricEntryRepository,
                         DomainEventPublisher domainEventPublisher,
                         Clock clock) {
        this.userProfileRepository = userProfileRepository;
        this.bodyMetricEntryRepository = bodyMetricEntryRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * Recompute and publish for a user; a no-op while height or weight is unknown.
     *
     * @return true when an event was published
     */
    public boolean publishEnergyProfile(UUID userId) {
        Optional<UserProfileEntity> maybeProfile = userProfileRepository.findById(userId);
        if (maybeProfile.isEmpty() || maybeProfile.get().getHeightCm() == null) {
            return false;
        }
        UserProfileEntity profile = maybeProfile.get();
        BigDecimal weightKg = bodyMetricEntryRepository.findTopByUserIdOrderByMeasuredAtDesc(userId)
                .map(BodyMetricEntryEntity::getWeightKg)
                .orElse(null);
        if (weightKg == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        double bmr = 10.0 * weightKg.doubleValue()
                + 6.25 * profile.getHeightCm().doubleValue()
                - 5.0 * ageYears(profile.getBirthDate(), now)
                + sexConstant(profile.getGender());
        double tdee = bmr * activityFactor(profile.getActivityLevel());
        boolean estimated = !hasRealAge(profile.getBirthDate(), now) || !hasDisclosedSex(profile.getGender());

        domainEventPublisher.publish(USER_EVENTS_TOPIC, "UserProfile", userId.toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), "EnergyProfileComputed", 1, now, SOURCE, userId,
                new EnergyProfileComputedPayload(
                        (int) Math.round(bmr),
                        (int) Math.round(tdee),
                        profile.getActivityLevel() == null ? null : profile.getActivityLevel().name(),
                        now,
                        estimated)));
        return true;
    }

    private boolean hasRealAge(LocalDate birthDate, Instant now) {
        return birthDate != null
                && Period.between(birthDate, LocalDate.ofInstant(now, clock.getZone())).getYears() > 0;
    }

    private static boolean hasDisclosedSex(Gender gender) {
        return gender == Gender.MALE || gender == Gender.FEMALE;
    }

    private int ageYears(LocalDate birthDate, Instant now) {
        if (birthDate == null) {
            return DEFAULT_AGE_YEARS;
        }
        int age = Period.between(birthDate, LocalDate.ofInstant(now, clock.getZone())).getYears();
        return age > 0 ? age : DEFAULT_AGE_YEARS;
    }

    private static int sexConstant(Gender gender) {
        if (gender == Gender.MALE) {
            return SEX_CONSTANT_MALE;
        }
        if (gender == Gender.FEMALE) {
            return SEX_CONSTANT_FEMALE;
        }
        return SEX_CONSTANT_NEUTRAL;
    }

    private static double activityFactor(ActivityLevel activityLevel) {
        Double factor = activityLevel == null ? null : ACTIVITY_FACTORS.get(activityLevel);
        return factor != null ? factor.doubleValue() : DEFAULT_ACTIVITY_FACTOR;
    }
}
