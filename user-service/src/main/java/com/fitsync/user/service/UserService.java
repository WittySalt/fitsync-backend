package com.fitsync.user.service;

import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.fitsync.user.dto.AccountDataExportPayload;
import com.fitsync.user.dto.AccountDataExportRequestView;
import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.dto.BodyMetricTrendResponse;
import com.fitsync.user.dto.BodyMetricView;
import com.fitsync.user.dto.CompleteOnboardingRequest;
import com.fitsync.user.dto.GoalReconcileResponse;
import com.fitsync.user.dto.PersonFactsReconcileResponse;
import com.fitsync.user.dto.CreateBodyMetricRequest;
import com.fitsync.user.dto.CurrentUserResponse;
import com.fitsync.user.dto.HealthReadinessView;
import com.fitsync.user.dto.ProgressInsightResponse;
import com.fitsync.user.dto.UpsertUserGoalRequest;
import com.fitsync.user.dto.UpsertHealthReadinessRequest;
import com.fitsync.user.dto.UpsertUserPreferencesRequest;
import com.fitsync.user.dto.UpsertUserProfileRequest;
import com.fitsync.user.dto.UserGoalView;
import com.fitsync.user.dto.UserPreferencesView;
import com.fitsync.user.dto.UserProfileView;
import com.fitsync.user.security.CurrentUserPrincipal;
import org.springframework.web.multipart.MultipartFile;
public interface UserService {

    CurrentUserResponse getCurrentUser(CurrentUserPrincipal principal);

    CurrentUserResponse completeOnboarding(CurrentUserPrincipal principal, CompleteOnboardingRequest request);

    UserProfileView getProfile(CurrentUserPrincipal principal);

    UserProfileView upsertProfile(CurrentUserPrincipal principal, UpsertUserProfileRequest request);

    UserProfileView uploadAvatar(CurrentUserPrincipal principal, MultipartFile file);

    UserPreferencesView getPreferences(CurrentUserPrincipal principal);

    UserPreferencesView upsertPreferences(CurrentUserPrincipal principal, UpsertUserPreferencesRequest request);

    HealthReadinessView getHealthReadiness(CurrentUserPrincipal principal);

    HealthReadinessView upsertHealthReadiness(CurrentUserPrincipal principal, UpsertHealthReadinessRequest request);

    BodyMetricView addBodyMetrics(CurrentUserPrincipal principal, CreateBodyMetricRequest request);

    BodyMetricView getLatestBodyMetrics(CurrentUserPrincipal principal);

    BodyMetricView getBodyMetric(CurrentUserPrincipal principal, UUID metricId);

    List<BodyMetricView> listBodyMetrics(CurrentUserPrincipal principal, Instant from, Instant to, int limit);

    BodyMetricTrendResponse getBodyMetricTrends(CurrentUserPrincipal principal, Instant from, Instant to);

    BodyMetricView replaceBodyMetric(CurrentUserPrincipal principal, UUID metricId, CreateBodyMetricRequest request);

    void deleteBodyMetric(CurrentUserPrincipal principal, UUID metricId);

    UserGoalView upsertGoal(CurrentUserPrincipal principal, GoalType goalType, UpsertUserGoalRequest request);

    List<UserGoalView> listGoals(CurrentUserPrincipal principal, GoalStatus status);

    void deleteGoal(CurrentUserPrincipal principal, GoalType goalType);

    AccountDataExportRequestView requestAccountDataExport(CurrentUserPrincipal principal);

    AccountDataExportRequestView getLatestAccountDataExport(CurrentUserPrincipal principal);

    AccountDataExportPayload downloadAccountDataExport(CurrentUserPrincipal principal, UUID exportRequestId);

    /** A null zone means the request named none, so the zone kept in the profile is used, else UTC. */
    ProgressInsightResponse getProgressInsight(CurrentUserPrincipal principal, ZoneId zone);

    GoalReconcileResponse republishActiveGoals();

    PersonFactsReconcileResponse republishPersonFacts();

    void deleteCurrentUser(CurrentUserPrincipal principal);
}
