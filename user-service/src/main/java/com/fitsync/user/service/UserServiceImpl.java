package com.fitsync.user.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.user.domain.AccountDataExportStatus;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.domain.MembershipStatus;
import com.fitsync.user.domain.MembershipTier;
import com.fitsync.user.domain.NotificationChannel;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import com.fitsync.user.domain.UnitSystem;
import com.fitsync.user.dto.AppPreferencesView;
import com.fitsync.user.dto.AccountDataExportPayload;
import com.fitsync.user.dto.AccountDataExportRequestView;
import com.fitsync.user.dto.BodyMetricTrend;
import com.fitsync.user.dto.BodyMetricTrendPoint;
import com.fitsync.user.dto.BodyMetricTrendResponse;
import com.fitsync.user.dto.BodyMetricView;
import com.fitsync.user.dto.CompleteOnboardingRequest;
import com.fitsync.user.dto.CreateBodyMetricRequest;
import com.fitsync.user.dto.CurrentUserResponse;
import com.fitsync.user.dto.HealthReadinessView;
import com.fitsync.user.dto.NotificationPreferencesView;
import com.fitsync.user.dto.NutritionPreferencesView;
import com.fitsync.user.dto.TrainingPreferencesView;
import com.fitsync.user.dto.UpsertHealthReadinessRequest;
import com.fitsync.user.dto.UpsertUserGoalRequest;
import com.fitsync.user.dto.UpsertUserPreferencesRequest;
import com.fitsync.user.dto.UpsertUserProfileRequest;
import com.fitsync.user.dto.UserGoalView;
import com.fitsync.user.dto.UserPreferencesView;
import com.fitsync.user.dto.UserProfileView;
import com.fitsync.user.entity.BodyMetricEntryEntity;
import com.fitsync.user.entity.AccountDataExportRequestEntity;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserGoalEntity;
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import com.fitsync.user.messaging.DomainEventPublisher;
import com.fitsync.user.repository.AccountDataExportRequestRepository;
import com.fitsync.user.repository.BodyMetricEntryRepository;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserGoalRepository;
import com.fitsync.user.repository.UserHealthReadinessRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final String SOURCE = "user-service";
    private static final String ONBOARDING_BASELINE_NOTE = "Initial onboarding baseline";

    private final DomainEventPublisher domainEventPublisher;
    private final AccountDataExportRequestRepository accountDataExportRequestRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserAppPreferencesRepository userAppPreferencesRepository;
    private final UserTrainingPreferencesRepository userTrainingPreferencesRepository;
    private final UserNutritionPreferencesRepository userNutritionPreferencesRepository;
    private final UserHealthReadinessRepository userHealthReadinessRepository;
    private final UserNotificationPreferencesRepository userNotificationPreferencesRepository;
    private final BodyMetricEntryRepository bodyMetricEntryRepository;
    private final UserGoalRepository userGoalRepository;

    public UserServiceImpl(DomainEventPublisher domainEventPublisher,
                           AccountDataExportRequestRepository accountDataExportRequestRepository,
                           UserProfileRepository userProfileRepository,
                           UserAppPreferencesRepository userAppPreferencesRepository,
                           UserTrainingPreferencesRepository userTrainingPreferencesRepository,
                           UserNutritionPreferencesRepository userNutritionPreferencesRepository,
                           UserHealthReadinessRepository userHealthReadinessRepository,
                           UserNotificationPreferencesRepository userNotificationPreferencesRepository,
                           BodyMetricEntryRepository bodyMetricEntryRepository,
                           UserGoalRepository userGoalRepository) {
        this.domainEventPublisher = domainEventPublisher;
        this.accountDataExportRequestRepository = accountDataExportRequestRepository;
        this.userProfileRepository = userProfileRepository;
        this.userAppPreferencesRepository = userAppPreferencesRepository;
        this.userTrainingPreferencesRepository = userTrainingPreferencesRepository;
        this.userNutritionPreferencesRepository = userNutritionPreferencesRepository;
        this.userHealthReadinessRepository = userHealthReadinessRepository;
        this.userNotificationPreferencesRepository = userNotificationPreferencesRepository;
        this.bodyMetricEntryRepository = bodyMetricEntryRepository;
        this.userGoalRepository = userGoalRepository;
    }

    @Override
    @Transactional
    public CurrentUserResponse getCurrentUser(CurrentUserPrincipal principal) {
        UserProfileEntity profile = getOrCreateProfileEntity(principal);
        return new CurrentUserResponse(
                principal.userId(),
                principal.email(),
                principal.role(),
                toProfileView(profile),
                getOrCreatePreferencesView(principal.userId()),
                userGoalRepository.findByUserIdAndStatusOrderByGoalTypeAsc(principal.userId(), GoalStatus.ACTIVE)
                        .stream()
                        .map(this::toGoalView)
                        .toList(),
                bodyMetricEntryRepository.findTopByUserIdOrderByMeasuredAtDesc(principal.userId())
                        .map(entry -> toBodyMetricView(entry, profile))
                        .orElse(null));
    }

    @Override
    @Transactional
    public CurrentUserResponse completeOnboarding(CurrentUserPrincipal principal, CompleteOnboardingRequest request) {
        String displayName = request.displayName().trim();
        String firstName = request.firstName() == null || request.firstName().isBlank()
                ? displayName.split("\\s+")[0]
                : request.firstName().trim();
        String lastName = request.lastName() == null ? "" : request.lastName().trim();
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "Asia/Singapore"
                : request.timezone().trim();
        LocalDate targetDate = request.targetDate() == null
                ? LocalDate.now().plusMonths(3)
                : request.targetDate();
        int calorieTarget = estimateDailyCalories(request);
        BigDecimal proteinTarget = estimateProteinTarget(request);

        upsertProfile(principal, new UpsertUserProfileRequest(
                displayName,
                firstName,
                lastName,
                null,
                request.birthDate(),
                request.gender(),
                request.heightCm(),
                request.activityLevel(),
                timezone,
                null,
                "",
                true));

        upsertPreferences(principal, new UpsertUserPreferencesRequest(
                new com.fitsync.user.dto.UpsertAppPreferencesRequest(
                        request.preferredUnits(),
                        request.language() == null ? LanguagePreference.EN : request.language(),
                        timezone,
                        request.privacyLevel() == null ? PrivacyLevel.PRIVATE : request.privacyLevel(),
                        false),
                new com.fitsync.user.dto.UpsertTrainingPreferencesRequest(
                        request.fitnessLevel(),
                        request.trainingLocation(),
                        request.weeklyWorkoutDays(),
                        request.preferredDurationMinutes(),
                        request.preferredWorkoutTime(),
                        request.intensityPreference(),
                        request.equipmentInventory(),
                        request.focusAreas()),
                new com.fitsync.user.dto.UpsertNutritionPreferencesRequest(
                        request.dietaryPattern(),
                        request.mealFrequency(),
                        request.calorieTrackingMode(),
                        request.macroPreference(),
                        request.waterGoalMl() == null ? Integer.valueOf(2500) : request.waterGoalMl()),
                new com.fitsync.user.dto.UpsertNotificationPreferencesRequest(
                        true,
                        false,
                        request.workoutReminderEnabled(),
                        request.mealReminderEnabled(),
                        request.weighInReminderEnabled(),
                        true,
                        false,
                        NotificationChannel.EMAIL,
                        false,
                        null,
                        null)));

        if (request.healthReadiness() != null) {
            upsertHealthReadiness(principal, request.healthReadiness());
        }

        upsertOnboardingBodyMetrics(principal, new CreateBodyMetricRequest(
                request.currentWeightKg(),
                request.bodyFatRate(),
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                com.fitsync.user.domain.MetricSource.MANUAL,
                ONBOARDING_BASELINE_NOTE));

        upsertGoal(principal, GoalType.WEIGHT, new UpsertUserGoalRequest(
                request.targetWeightKg(),
                request.preferredUnits() == UnitSystem.IMPERIAL ? "lb" : "kg",
                targetDate,
                GoalStatus.ACTIVE,
                "Initial target from onboarding"));
        upsertGoal(principal, GoalType.WORKOUT_FREQUENCY, new UpsertUserGoalRequest(
                BigDecimal.valueOf(request.weeklyWorkoutDays()),
                "sessions/week",
                targetDate,
                GoalStatus.ACTIVE,
                "Initial training frequency from onboarding"));
        upsertGoal(principal, GoalType.CALORIES, new UpsertUserGoalRequest(
                BigDecimal.valueOf(calorieTarget),
                "kcal",
                targetDate,
                GoalStatus.ACTIVE,
                "Estimated daily calorie target from onboarding"));
        upsertGoal(principal, GoalType.PROTEIN, new UpsertUserGoalRequest(
                proteinTarget,
                "g",
                targetDate,
                GoalStatus.ACTIVE,
                "Estimated daily protein target from onboarding"));

        return getCurrentUser(principal);
    }

    @Override
    @Transactional
    public UserProfileView getProfile(CurrentUserPrincipal principal) {
        return toProfileView(getOrCreateProfileEntity(principal));
    }

    @Override
    @Transactional
    public UserProfileView upsertProfile(CurrentUserPrincipal principal, UpsertUserProfileRequest request) {
        UserProfileEntity entity = getOrCreateProfileEntity(principal);
        entity.setDisplayName(request.displayName());
        entity.setFirstName(request.firstName());
        entity.setLastName(request.lastName());
        entity.setAvatarUrl(request.avatarUrl());
        entity.setBirthDate(request.birthDate());
        entity.setGender(request.gender());
        entity.setHeightCm(request.heightCm());
        entity.setActivityLevel(request.activityLevel());
        entity.setTimezone(request.timezone());
        entity.setLocationDisplay(request.locationDisplay());
        entity.setBio(request.bio());
        entity.setOnboardingCompleted(request.onboardingCompleted());
        UserProfileEntity saved = userProfileRepository.save(entity);
        domainEventPublisher.publish(
                "user-events", "UserProfile", principal.userId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), "UserProfileUpdated", 1, Instant.now(), SOURCE, principal.userId(),
                new UserProfileUpdatedPayload(
                        saved.getDisplayName(),
                        saved.getHeightCm(),
                        saved.getActivityLevel() == null ? null : saved.getActivityLevel().name(),
                        saved.isOnboardingCompleted(),
                        List.of("displayName", "firstName", "lastName", "avatarUrl", "birthDate",
                                "gender", "heightCm", "activityLevel", "timezone", "bio", "onboardingCompleted"))));
        return toProfileView(saved);
    }

    @Override
    @Transactional
    public UserPreferencesView getPreferences(CurrentUserPrincipal principal) {
        return getOrCreatePreferencesView(principal.userId());
    }

    @Override
    @Transactional
    public UserPreferencesView upsertPreferences(CurrentUserPrincipal principal, UpsertUserPreferencesRequest request) {
        UserAppPreferencesEntity app = getOrCreateAppPreferencesEntity(principal.userId());
        app.setPreferredUnits(request.app().preferredUnits());
        app.setLanguage(request.app().language());
        app.setTimezone(request.app().timezone());
        app.setPrivacyLevel(request.app().privacyLevel());
        app.setDataSharingConsent(request.app().dataSharingConsent());

        UserTrainingPreferencesEntity training = getOrCreateTrainingPreferencesEntity(principal.userId());
        training.setFitnessLevel(request.training().fitnessLevel());
        training.setTrainingLocation(request.training().trainingLocation());
        training.setWeeklyWorkoutDays(request.training().weeklyWorkoutDays());
        training.setPreferredDurationMinutes(request.training().preferredDurationMinutes());
        training.setPreferredWorkoutTime(request.training().preferredWorkoutTime());
        training.setIntensityPreference(request.training().intensityPreference());
        training.setEquipmentInventory(normalizeEquipmentInventory(request.training().equipmentInventory()));
        training.setFocusAreas(normalizeFocusAreas(request.training().focusAreas()));

        UserNutritionPreferencesEntity nutrition = getOrCreateNutritionPreferencesEntity(principal.userId());
        nutrition.setDietaryPattern(request.nutrition().dietaryPattern());
        nutrition.setMealFrequency(request.nutrition().mealFrequency());
        nutrition.setCalorieTrackingMode(request.nutrition().calorieTrackingMode());
        nutrition.setMacroPreference(request.nutrition().macroPreference());
        nutrition.setWaterGoalMl(request.nutrition().waterGoalMl());

        UserNotificationPreferencesEntity notifications = getOrCreateNotificationPreferencesEntity(principal.userId());
        notifications.setPushEnabled(request.notifications().pushEnabled());
        notifications.setEmailEnabled(request.notifications().emailEnabled());
        notifications.setWorkoutReminderEnabled(request.notifications().workoutReminderEnabled());
        notifications.setMealReminderEnabled(request.notifications().mealReminderEnabled());
        notifications.setWeighInReminderEnabled(request.notifications().weighInReminderEnabled());
        notifications.setGoalProgressReminderEnabled(request.notifications().goalProgressReminderEnabled());
        if (request.notifications().weeklyProgressReportEnabled() != null) {
            notifications.setWeeklyProgressReportEnabled(request.notifications().weeklyProgressReportEnabled());
        }
        if (request.notifications().weeklyProgressReportChannel() != null) {
            notifications.setWeeklyProgressReportChannel(request.notifications().weeklyProgressReportChannel());
        }
        notifications.setMarketingEmailsEnabled(request.notifications().marketingEmailsEnabled());
        notifications.setQuietHoursStart(request.notifications().quietHoursStart());
        notifications.setQuietHoursEnd(request.notifications().quietHoursEnd());

        UserAppPreferencesEntity savedApp = userAppPreferencesRepository.save(app);
        UserTrainingPreferencesEntity savedTraining = userTrainingPreferencesRepository.save(training);
        UserNutritionPreferencesEntity savedNutrition = userNutritionPreferencesRepository.save(nutrition);
        UserNotificationPreferencesEntity savedNotifications =
                userNotificationPreferencesRepository.save(notifications);
        domainEventPublisher.publish(
                "user-events", "UserPreferences", principal.userId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), "UserPreferencesUpdated", 1, Instant.now(), SOURCE, principal.userId(),
                new UserPreferencesUpdatedPayload(
                        savedApp.getPreferredUnits().getCode(),
                        savedTraining.getWeeklyWorkoutDays(),
                        savedNutrition.getDietaryPattern() == null
                                ? null
                                : savedNutrition.getDietaryPattern().getCode(),
                        savedNotifications.isPushEnabled() || savedNotifications.isEmailEnabled(),
                        savedTraining.getEquipmentInventory().stream().map(TrainingEquipment::getCode).toList(),
                        savedTraining.getFocusAreas().stream().map(TrainingFocusArea::getCode).toList())));
        return toPreferencesView(savedApp, savedTraining, savedNutrition, savedNotifications);
    }

    @Override
    @Transactional(readOnly = true)
    public HealthReadinessView getHealthReadiness(CurrentUserPrincipal principal) {
        return toHealthReadinessView(getOrCreateHealthReadinessEntity(principal.userId()));
    }

    @Override
    @Transactional
    public HealthReadinessView upsertHealthReadiness(CurrentUserPrincipal principal, UpsertHealthReadinessRequest request) {
        UserHealthReadinessEntity entity = getOrCreateHealthReadinessEntity(principal.userId());
        entity.setHeartCondition(request.heartCondition());
        entity.setChestPainActivity(request.chestPainActivity());
        entity.setDizziness(request.dizziness());
        entity.setBoneJointIssue(request.boneJointIssue());
        entity.setBpOrHeartMeds(request.bpOrHeartMeds());
        entity.setOtherReason(request.otherReason());
        entity.setInjuryAreas(normalizeInjuryAreas(request.injuryAreas()));
        entity.setAcknowledgedDisclaimerAt(request.acknowledgedDisclaimerAt());
        return toHealthReadinessView(userHealthReadinessRepository.save(entity));
    }

    @Override
    @Transactional
    public BodyMetricView addBodyMetrics(CurrentUserPrincipal principal, CreateBodyMetricRequest request) {
        validateBodyMetricRequest(request);
        UserProfileEntity profile = getOrCreateProfileEntity(principal);
        BodyMetricEntryEntity entity = new BodyMetricEntryEntity();
        entity.setUserId(principal.userId());
        applyBodyMetricRequest(entity, request);
        BodyMetricEntryEntity saved = bodyMetricEntryRepository.save(entity);
        publishBodyMetricsRecorded(principal.userId(), saved);
        return toBodyMetricView(saved, profile);
    }

    private BodyMetricView upsertOnboardingBodyMetrics(CurrentUserPrincipal principal, CreateBodyMetricRequest request) {
        validateBodyMetricRequest(request);
        UserProfileEntity profile = getOrCreateProfileEntity(principal);
        BodyMetricEntryEntity entity = bodyMetricEntryRepository
                .findTopByUserIdAndNoteOrderByMeasuredAtDesc(principal.userId(), ONBOARDING_BASELINE_NOTE)
                .orElseGet(BodyMetricEntryEntity::new);
        entity.setUserId(principal.userId());
        applyBodyMetricRequest(entity, request);
        BodyMetricEntryEntity saved = bodyMetricEntryRepository.save(entity);
        publishBodyMetricsRecorded(principal.userId(), saved);
        return toBodyMetricView(saved, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public BodyMetricView getLatestBodyMetrics(CurrentUserPrincipal principal) {
        UserProfileEntity profile = userProfileRepository.findById(principal.userId()).orElse(null);
        return bodyMetricEntryRepository.findTopByUserIdOrderByMeasuredAtDesc(principal.userId())
                .map(entry -> toBodyMetricView(entry, profile))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BodyMetricView getBodyMetric(CurrentUserPrincipal principal, UUID metricId) {
        UserProfileEntity profile = userProfileRepository.findById(principal.userId()).orElse(null);
        BodyMetricEntryEntity entity = getOwnedBodyMetric(principal.userId(), metricId);
        return toBodyMetricView(entity, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BodyMetricView> listBodyMetrics(CurrentUserPrincipal principal, Instant from, Instant to, int limit) {
        validateTimeRange(from, to);
        UserProfileEntity profile = userProfileRepository.findById(principal.userId()).orElse(null);
        return bodyMetricEntryRepository.findHistory(principal.userId(), from, to, PageRequest.of(0, limit)).stream()
                .map(entry -> toBodyMetricView(entry, profile))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BodyMetricTrendResponse getBodyMetricTrends(CurrentUserPrincipal principal, Instant from, Instant to) {
        validateTimeRange(from, to);
        UserProfileEntity profile = userProfileRepository.findById(principal.userId()).orElse(null);
        List<BodyMetricEntryEntity> entries = bodyMetricEntryRepository.findTrendSource(principal.userId(), from, to);
        return new BodyMetricTrendResponse(
                from,
                to,
                entries.size(),
                trend(entries, BodyMetricEntryEntity::getWeightKg),
                trend(entries, BodyMetricEntryEntity::getBodyFatRate),
                trend(entries, BodyMetricEntryEntity::getWaistCm),
                trend(entries, entry -> calculateBmi(
                        entry.getWeightKg(), profile == null ? null : profile.getHeightCm())));
    }

    @Override
    @Transactional
    public BodyMetricView replaceBodyMetric(CurrentUserPrincipal principal,
                                            UUID metricId,
                                            CreateBodyMetricRequest request) {
        validateBodyMetricRequest(request);
        UserProfileEntity profile = userProfileRepository.findById(principal.userId()).orElse(null);
        BodyMetricEntryEntity entity = getOwnedBodyMetric(principal.userId(), metricId);
        applyBodyMetricRequest(entity, request);
        BodyMetricEntryEntity saved = bodyMetricEntryRepository.save(entity);
        publishBodyMetricsRecorded(principal.userId(), saved);
        return toBodyMetricView(saved, profile);
    }

    @Override
    @Transactional
    public void deleteBodyMetric(CurrentUserPrincipal principal, UUID metricId) {
        BodyMetricEntryEntity entity = getOwnedBodyMetric(principal.userId(), metricId);
        bodyMetricEntryRepository.delete(entity);
    }

    @Override
    @Transactional
    public UserGoalView upsertGoal(CurrentUserPrincipal principal, GoalType goalType, UpsertUserGoalRequest request) {
        UserGoalEntity entity = userGoalRepository.findByUserIdAndGoalType(principal.userId(), goalType)
                .orElseGet(UserGoalEntity::new);
        entity.setUserId(principal.userId());
        entity.setGoalType(goalType);
        entity.setTargetValue(request.targetValue());
        entity.setTargetUnit(request.targetUnit());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        UserGoalEntity saved = userGoalRepository.save(entity);
        domainEventPublisher.publish(
                "progress-events", "UserGoal", saved.getGoalId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), "GoalUpdated", 1, Instant.now(), SOURCE, principal.userId(),
                new GoalUpdatedPayload(
                        saved.getGoalId(), saved.getGoalType().name(), saved.getTargetValue(),
                        saved.getTargetUnit(), saved.getTargetDate(), saved.getStatus().name())));
        return toGoalView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGoalView> listGoals(CurrentUserPrincipal principal, GoalStatus status) {
        List<UserGoalEntity> goals = status == null
                ? userGoalRepository.findByUserIdOrderByGoalTypeAsc(principal.userId())
                : userGoalRepository.findByUserIdAndStatusOrderByGoalTypeAsc(principal.userId(), status);
        return goals.stream()
                .map(this::toGoalView)
                .toList();
    }

    @Override
    @Transactional
    public AccountDataExportRequestView requestAccountDataExport(CurrentUserPrincipal principal) {
        Instant now = Instant.now();
        AccountDataExportRequestEntity entity = new AccountDataExportRequestEntity();
        entity.setUserId(principal.userId());
        entity.setStatus(AccountDataExportStatus.READY);
        entity.setRequestedAt(now);
        entity.setCompletedAt(now);
        return toAccountDataExportRequestView(accountDataExportRequestRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDataExportRequestView getLatestAccountDataExport(CurrentUserPrincipal principal) {
        return accountDataExportRequestRepository.findTopByUserIdOrderByRequestedAtDesc(principal.userId())
                .map(this::toAccountDataExportRequestView)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDataExportPayload downloadAccountDataExport(CurrentUserPrincipal principal, UUID exportRequestId) {
        AccountDataExportRequestEntity exportRequest = accountDataExportRequestRepository
                .findByExportRequestIdAndUserId(exportRequestId, principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Account data export request not found"));
        if (exportRequest.getStatus() != AccountDataExportStatus.READY) {
            throw new IllegalArgumentException("Account data export is not ready for download");
        }

        UserProfileEntity profile = getOrCreateProfileEntity(principal);
        CurrentUserResponse account = new CurrentUserResponse(
                principal.userId(),
                principal.email(),
                principal.role(),
                toProfileView(profile),
                getOrCreatePreferencesView(principal.userId()),
                listGoals(principal, null),
                bodyMetricEntryRepository.findTopByUserIdOrderByMeasuredAtDesc(principal.userId())
                        .map(entry -> toBodyMetricView(entry, profile))
                        .orElse(null));

        List<BodyMetricView> bodyMetrics = bodyMetricEntryRepository.findHistory(
                        principal.userId(),
                        null,
                        null,
                        PageRequest.of(0, 200))
                .stream()
                .map(entry -> toBodyMetricView(entry, profile))
                .toList();

        return new AccountDataExportPayload(
                exportRequest.getCompletedAt(),
                account,
                listGoals(principal, null),
                bodyMetrics);
    }

    private UserProfileEntity getOrCreateProfileEntity(CurrentUserPrincipal principal) {
        return userProfileRepository.findById(principal.userId()).orElseGet(() -> {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(principal.userId());
            entity.setDisplayName(principal.displayName());
            entity.setMembershipTier(MembershipTier.MEMBER);
            entity.setMembershipStatus(MembershipStatus.ACTIVE);
            entity.setOnboardingCompleted(false);
            return userProfileRepository.save(entity);
        });
    }

    private UserPreferencesView getOrCreatePreferencesView(UUID userId) {
        return toPreferencesView(
                getOrCreateAppPreferencesEntity(userId),
                getOrCreateTrainingPreferencesEntity(userId),
                getOrCreateNutritionPreferencesEntity(userId),
                getOrCreateNotificationPreferencesEntity(userId));
    }

    private UserAppPreferencesEntity getOrCreateAppPreferencesEntity(UUID userId) {
        return userAppPreferencesRepository.findById(userId).orElseGet(() -> {
            UserAppPreferencesEntity entity = new UserAppPreferencesEntity();
            entity.setUserId(userId);
            entity.setPreferredUnits(UnitSystem.METRIC);
            entity.setLanguage(LanguagePreference.EN);
            entity.setPrivacyLevel(PrivacyLevel.PRIVATE);
            entity.setDataSharingConsent(false);
            return userAppPreferencesRepository.save(entity);
        });
    }

    private UserTrainingPreferencesEntity getOrCreateTrainingPreferencesEntity(UUID userId) {
        return userTrainingPreferencesRepository.findById(userId).orElseGet(() -> {
            UserTrainingPreferencesEntity entity = new UserTrainingPreferencesEntity();
            entity.setUserId(userId);
            entity.setFitnessLevel(FitnessLevel.BEGINNER);
            entity.setTrainingLocation(TrainingLocation.MIXED);
            entity.setIntensityPreference(IntensityPreference.MODERATE);
            return userTrainingPreferencesRepository.save(entity);
        });
    }

    private UserHealthReadinessEntity getOrCreateHealthReadinessEntity(UUID userId) {
        return userHealthReadinessRepository.findById(userId).orElseGet(() -> {
            UserHealthReadinessEntity entity = new UserHealthReadinessEntity();
            entity.setUserId(userId);
            return userHealthReadinessRepository.save(entity);
        });
    }

    private UserNutritionPreferencesEntity getOrCreateNutritionPreferencesEntity(UUID userId) {
        return userNutritionPreferencesRepository.findById(userId).orElseGet(() -> {
            UserNutritionPreferencesEntity entity = new UserNutritionPreferencesEntity();
            entity.setUserId(userId);
            entity.setDietaryPattern(DietaryPreference.NONE);
            entity.setCalorieTrackingMode(CalorieTrackingMode.FLEXIBLE);
            entity.setMacroPreference(MacroPreference.BALANCED);
            return userNutritionPreferencesRepository.save(entity);
        });
    }

    private UserNotificationPreferencesEntity getOrCreateNotificationPreferencesEntity(UUID userId) {
        return userNotificationPreferencesRepository.findById(userId).orElseGet(() -> {
            UserNotificationPreferencesEntity entity = new UserNotificationPreferencesEntity();
            entity.setUserId(userId);
            entity.setPushEnabled(true);
            entity.setEmailEnabled(false);
            entity.setWorkoutReminderEnabled(true);
            entity.setMealReminderEnabled(false);
            entity.setWeighInReminderEnabled(false);
            entity.setGoalProgressReminderEnabled(true);
            entity.setWeeklyProgressReportEnabled(false);
            entity.setWeeklyProgressReportChannel(NotificationChannel.EMAIL);
            entity.setMarketingEmailsEnabled(false);
            return userNotificationPreferencesRepository.save(entity);
        });
    }

    private BodyMetricEntryEntity getOwnedBodyMetric(UUID userId, UUID metricId) {
        return bodyMetricEntryRepository.findByMetricIdAndUserId(metricId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Body metric not found"));
    }

    private void applyBodyMetricRequest(BodyMetricEntryEntity entity, CreateBodyMetricRequest request) {
        entity.setWeightKg(request.weightKg());
        entity.setBodyFatRate(request.bodyFatRate());
        entity.setWaistCm(request.waistCm());
        entity.setHipCm(request.hipCm());
        entity.setChestCm(request.chestCm());
        entity.setArmCm(request.armCm());
        entity.setThighCm(request.thighCm());
        entity.setCalfCm(request.calfCm());
        entity.setMeasuredAt(request.measuredAt());
        entity.setSource(request.source());
        entity.setNote(request.note());
    }

    private void publishBodyMetricsRecorded(UUID userId, BodyMetricEntryEntity saved) {
        domainEventPublisher.publish(
                "progress-events", "BodyMetricEntry", saved.getMetricId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), "BodyMetricsRecorded", 1, Instant.now(), SOURCE, userId,
                new BodyMetricsRecordedPayload(
                        saved.getMetricId(),
                        saved.getWeightKg(),
                        saved.getBodyFatRate(),
                        saved.getWaistCm(),
                        saved.getMeasuredAt())));
    }

    private UserProfileView toProfileView(UserProfileEntity entity) {
        return new UserProfileView(
                entity.getDisplayName(), entity.getFirstName(), entity.getLastName(), entity.getAvatarUrl(),
                entity.getBirthDate(), entity.getGender(), entity.getHeightCm(), entity.getActivityLevel(),
                entity.getTimezone(), entity.getLocationDisplay(), entity.getBio(),
                entity.getMembershipTier(), entity.getMembershipStatus(), entity.isOnboardingCompleted());
    }

    private UserPreferencesView toPreferencesView(UserAppPreferencesEntity app,
                                                  UserTrainingPreferencesEntity training,
                                                  UserNutritionPreferencesEntity nutrition,
                                                  UserNotificationPreferencesEntity notifications) {
        return new UserPreferencesView(
                new AppPreferencesView(
                        app.getPreferredUnits(),
                        app.getLanguage(),
                        app.getTimezone(),
                        app.getPrivacyLevel(),
                        app.isDataSharingConsent()),
                new TrainingPreferencesView(
                        training.getFitnessLevel(),
                        training.getTrainingLocation(),
                        training.getWeeklyWorkoutDays(),
                        training.getPreferredDurationMinutes(),
                        training.getPreferredWorkoutTime(),
                        training.getIntensityPreference(),
                        training.getEquipmentInventory(),
                        training.getFocusAreas()),
                new NutritionPreferencesView(
                        nutrition.getDietaryPattern(),
                        nutrition.getMealFrequency(),
                        nutrition.getCalorieTrackingMode(),
                        nutrition.getMacroPreference(),
                        nutrition.getWaterGoalMl()),
                new NotificationPreferencesView(
                        notifications.isPushEnabled(),
                        notifications.isEmailEnabled(),
                        notifications.isWorkoutReminderEnabled(),
                        notifications.isMealReminderEnabled(),
                        notifications.isWeighInReminderEnabled(),
                        notifications.isGoalProgressReminderEnabled(),
                        notifications.isWeeklyProgressReportEnabled(),
                        notifications.getWeeklyProgressReportChannel(),
                        notifications.isMarketingEmailsEnabled(),
                        notifications.getQuietHoursStart(),
                        notifications.getQuietHoursEnd()));
    }

    private HealthReadinessView toHealthReadinessView(UserHealthReadinessEntity entity) {
        return new HealthReadinessView(
                entity.isHeartCondition(),
                entity.isChestPainActivity(),
                entity.isDizziness(),
                entity.isBoneJointIssue(),
                entity.isBpOrHeartMeds(),
                entity.isOtherReason(),
                entity.getInjuryAreas(),
                entity.getAcknowledgedDisclaimerAt());
    }

    private UserGoalView toGoalView(UserGoalEntity entity) {
        return new UserGoalView(
                entity.getGoalId(), entity.getGoalType(), entity.getTargetValue(), entity.getTargetUnit(),
                entity.getTargetDate(), entity.getStatus(), entity.getNotes());
    }

    private AccountDataExportRequestView toAccountDataExportRequestView(AccountDataExportRequestEntity entity) {
        return new AccountDataExportRequestView(
                entity.getExportRequestId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getCompletedAt(),
                entity.getStatus() == AccountDataExportStatus.READY);
    }

    private BodyMetricView toBodyMetricView(BodyMetricEntryEntity entity, UserProfileEntity profile) {
        return new BodyMetricView(
                entity.getMetricId(), entity.getWeightKg(), entity.getBodyFatRate(), entity.getWaistCm(),
                entity.getHipCm(), entity.getChestCm(), entity.getArmCm(), entity.getThighCm(), entity.getCalfCm(),
                calculateBmi(entity.getWeightKg(), profile == null ? null : profile.getHeightCm()),
                entity.getMeasuredAt(), entity.getSource(), entity.getNote());
    }

    private BigDecimal calculateBmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightMeters = heightCm.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return weightKg.divide(heightMeters.multiply(heightMeters), 2, RoundingMode.HALF_UP);
    }

    private int estimateDailyCalories(CompleteOnboardingRequest request) {
        int age = request.birthDate() == null
                ? 30
                : Math.max(16, LocalDate.now().getYear() - request.birthDate().getYear());
        BigDecimal bmr = BigDecimal.valueOf(10).multiply(request.currentWeightKg())
                .add(BigDecimal.valueOf(6.25).multiply(request.heightCm()))
                .subtract(BigDecimal.valueOf(5L * age))
                .add(genderAdjustment(request.gender()));
        BigDecimal tdee = bmr.multiply(activityMultiplier(request.activityLevel()));
        BigDecimal goalAdjustment = request.targetWeightKg().compareTo(request.currentWeightKg()) < 0
                ? BigDecimal.valueOf(-400)
                : request.targetWeightKg().compareTo(request.currentWeightKg()) > 0
                        ? BigDecimal.valueOf(250)
                        : BigDecimal.ZERO;
        return Math.max(1200, tdee.add(goalAdjustment).setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private BigDecimal estimateProteinTarget(CompleteOnboardingRequest request) {
        BigDecimal multiplier = request.targetWeightKg().compareTo(request.currentWeightKg()) < 0
                || request.macroPreference() == MacroPreference.HIGH_PROTEIN
                ? BigDecimal.valueOf(1.8)
                : BigDecimal.valueOf(1.6);
        return request.currentWeightKg().multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal genderAdjustment(com.fitsync.user.domain.Gender gender) {
        if (gender == com.fitsync.user.domain.Gender.MALE) {
            return BigDecimal.valueOf(5);
        }
        if (gender == com.fitsync.user.domain.Gender.FEMALE) {
            return BigDecimal.valueOf(-161);
        }
        return BigDecimal.valueOf(-78);
    }

    private BigDecimal activityMultiplier(com.fitsync.user.domain.ActivityLevel activityLevel) {
        return switch (activityLevel) {
            case SEDENTARY -> BigDecimal.valueOf(1.2);
            case LIGHTLY_ACTIVE -> BigDecimal.valueOf(1.375);
            case MODERATELY_ACTIVE -> BigDecimal.valueOf(1.55);
            case VERY_ACTIVE -> BigDecimal.valueOf(1.725);
            case ATHLETE -> BigDecimal.valueOf(1.9);
        };
    }

    private BodyMetricTrend trend(List<BodyMetricEntryEntity> entries,
                                  Function<BodyMetricEntryEntity, BigDecimal> extractor) {
        BodyMetricTrendPoint first = null;
        BodyMetricTrendPoint latest = null;
        for (BodyMetricEntryEntity entry : entries) {
            BigDecimal value = extractor.apply(entry);
            if (value == null) {
                continue;
            }
            BodyMetricTrendPoint point = new BodyMetricTrendPoint(value, entry.getMeasuredAt());
            if (first == null) {
                first = point;
            }
            latest = point;
        }
        BigDecimal change = first == null || latest == null ? null : latest.value().subtract(first.value());
        return new BodyMetricTrend(first, latest, change);
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }

    private void validateBodyMetricRequest(CreateBodyMetricRequest request) {
        if (request.weightKg() == null
                && request.bodyFatRate() == null
                && request.waistCm() == null
                && request.hipCm() == null
                && request.chestCm() == null
                && request.armCm() == null
                && request.thighCm() == null
                && request.calfCm() == null) {
            throw new IllegalArgumentException("At least one body measurement must be provided");
        }
    }

    private List<TrainingEquipment> normalizeEquipmentInventory(List<TrainingEquipment> equipmentInventory) {
        List<TrainingEquipment> normalized = distinctPreservingOrder(equipmentInventory);
        if (normalized.contains(TrainingEquipment.NONE) && normalized.size() > 1) {
            throw new IllegalArgumentException("equipmentInventory cannot combine NONE with other equipment");
        }
        return normalized;
    }

    private List<TrainingFocusArea> normalizeFocusAreas(List<TrainingFocusArea> focusAreas) {
        return distinctPreservingOrder(focusAreas);
    }

    private List<String> normalizeInjuryAreas(List<String> injuryAreas) {
        return distinctPreservingOrder(injuryAreas).stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    private <T> List<T> distinctPreservingOrder(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }
}
