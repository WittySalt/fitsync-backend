package com.fitsync.user.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.dto.AccountDataExportPayload;
import com.fitsync.user.dto.AccountDataExportRequestView;
import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.dto.BodyMetricTrendResponse;
import com.fitsync.user.dto.BodyMetricView;
import com.fitsync.user.dto.CompleteOnboardingRequest;
import com.fitsync.user.dto.CreateBodyMetricRequest;
import com.fitsync.user.dto.CurrentUserResponse;
import com.fitsync.user.dto.HealthReadinessView;
import com.fitsync.user.dto.UpsertHealthReadinessRequest;
import com.fitsync.user.dto.UpsertUserGoalRequest;
import com.fitsync.user.dto.UpsertUserPreferencesRequest;
import com.fitsync.user.dto.UpsertUserProfileRequest;
import com.fitsync.user.dto.UserGoalView;
import com.fitsync.user.dto.UserPreferencesView;
import com.fitsync.user.dto.UserProfileView;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Authentication authentication) {
        return userService.getCurrentUser(currentPrincipal(authentication));
    }

    @PostMapping("/me/onboarding")
    public CurrentUserResponse completeOnboarding(Authentication authentication,
                                                  @Valid @RequestBody CompleteOnboardingRequest request) {
        return userService.completeOnboarding(currentPrincipal(authentication), request);
    }

    @GetMapping("/me/profile")
    public UserProfileView profile(Authentication authentication) {
        return userService.getProfile(currentPrincipal(authentication));
    }

    @PutMapping("/me/profile")
    public UserProfileView upsertProfile(Authentication authentication,
                                         @Valid @RequestBody UpsertUserProfileRequest request) {
        return userService.upsertProfile(currentPrincipal(authentication), request);
    }

    @PostMapping(value = "/me/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileView uploadAvatar(Authentication authentication,
                                        @RequestPart("file") MultipartFile file) {
        return userService.uploadAvatar(currentPrincipal(authentication), file);
    }

    @GetMapping("/me/preferences")
    public UserPreferencesView preferences(Authentication authentication) {
        return userService.getPreferences(currentPrincipal(authentication));
    }

    @PutMapping("/me/preferences")
    public UserPreferencesView upsertPreferences(Authentication authentication,
                                                 @Valid @RequestBody UpsertUserPreferencesRequest request) {
        return userService.upsertPreferences(currentPrincipal(authentication), request);
    }

    @GetMapping("/me/health-readiness")
    public HealthReadinessView healthReadiness(Authentication authentication) {
        return userService.getHealthReadiness(currentPrincipal(authentication));
    }

    @PutMapping("/me/health-readiness")
    public HealthReadinessView upsertHealthReadiness(Authentication authentication,
                                                     @Valid @RequestBody UpsertHealthReadinessRequest request) {
        return userService.upsertHealthReadiness(currentPrincipal(authentication), request);
    }

    @PostMapping("/me/body-metrics")
    @ResponseStatus(HttpStatus.CREATED)
    public BodyMetricView createBodyMetrics(Authentication authentication,
                                            @Valid @RequestBody CreateBodyMetricRequest request) {
        return userService.addBodyMetrics(currentPrincipal(authentication), request);
    }

    @GetMapping("/me/body-metrics/latest")
    public BodyMetricView latestBodyMetrics(Authentication authentication) {
        return userService.getLatestBodyMetrics(currentPrincipal(authentication));
    }

    @GetMapping("/me/body-metrics/{metricId}")
    public BodyMetricView bodyMetric(Authentication authentication,
                                     @PathVariable UUID metricId) {
        return userService.getBodyMetric(currentPrincipal(authentication), metricId);
    }

    @GetMapping("/me/body-metrics")
    public List<BodyMetricView> bodyMetrics(Authentication authentication,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return userService.listBodyMetrics(currentPrincipal(authentication), from, to, limit);
    }

    @GetMapping("/me/body-metrics/trends")
    public BodyMetricTrendResponse bodyMetricTrends(Authentication authentication,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to) {
        return userService.getBodyMetricTrends(currentPrincipal(authentication), from, to);
    }

    @PutMapping("/me/body-metrics/{metricId}")
    public BodyMetricView replaceBodyMetric(Authentication authentication,
                                            @PathVariable UUID metricId,
                                            @Valid @RequestBody CreateBodyMetricRequest request) {
        return userService.replaceBodyMetric(currentPrincipal(authentication), metricId, request);
    }

    @DeleteMapping("/me/body-metrics/{metricId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBodyMetric(Authentication authentication,
                                 @PathVariable UUID metricId) {
        userService.deleteBodyMetric(currentPrincipal(authentication), metricId);
    }

    @GetMapping("/me/goals")
    public List<UserGoalView> goals(Authentication authentication,
                                    @RequestParam(required = false) GoalStatus status) {
        return userService.listGoals(currentPrincipal(authentication), status);
    }

    @PutMapping("/me/goals/{goalType}")
    public UserGoalView upsertGoal(Authentication authentication,
                                   @PathVariable GoalType goalType,
                                   @Valid @RequestBody UpsertUserGoalRequest request) {
        return userService.upsertGoal(currentPrincipal(authentication), goalType, request);
    }

    @PostMapping("/me/data-export/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDataExportRequestView requestDataExport(Authentication authentication) {
        return userService.requestAccountDataExport(currentPrincipal(authentication));
    }

    @GetMapping("/me/data-export/requests/latest")
    public AccountDataExportRequestView latestDataExport(Authentication authentication) {
        return userService.getLatestAccountDataExport(currentPrincipal(authentication));
    }

    @GetMapping("/me/data-export/requests/{exportRequestId}/download")
    public ResponseEntity<AccountDataExportPayload> downloadDataExport(Authentication authentication,
                                                                       @PathVariable UUID exportRequestId) {
        AccountDataExportPayload payload = userService.downloadAccountDataExport(
                currentPrincipal(authentication), exportRequestId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fitsync-account-export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
