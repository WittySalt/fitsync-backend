package com.fitsync.workout.controller;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseMetadataView;
import com.fitsync.workout.dto.ExerciseRecordsView;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExportView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionResumeView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.WorkoutService;
import com.fitsync.web.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@Validated
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final ExerciseService exerciseService;

    public WorkoutController(WorkoutService workoutService, ExerciseService exerciseService) {
        this.workoutService = workoutService;
        this.exerciseService = exerciseService;
    }

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseView createExercise(Authentication authentication,
                                       @Valid @RequestBody CreateExerciseRequest request) {
        return exerciseService.createCustomExercise(currentPrincipal(authentication), request);
    }

    @GetMapping("/exercises")
    public PageResponse<ExerciseView> listExercises(
            Authentication authentication,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ExerciseType exerciseType,
            @RequestParam(required = false) MuscleGroup primaryMuscleGroup,
            @RequestParam(required = false) ExerciseEquipment equipment,
            @RequestParam(required = false) WorkoutDifficulty difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return exerciseService.listExercises(
                currentPrincipal(authentication), q, exerciseType, primaryMuscleGroup, equipment, difficulty, page, size);
    }

    @Operation(summary = "Get exercise and workout metadata")
    @GetMapping("/exercises/metadata")
    public ExerciseMetadataView getExerciseMetadata() {
        return exerciseService.getExerciseMetadata();
    }

    @Operation(summary = "Get an accessible exercise")
    @GetMapping("/exercises/{exerciseId}")
    public ExerciseView getExercise(Authentication authentication,
                                    @PathVariable UUID exerciseId) {
        return exerciseService.getExercise(currentPrincipal(authentication), exerciseId);
    }

    @Operation(summary = "Get completed set history for an accessible exercise")
    @GetMapping("/exercises/{exerciseId}/history")
    public PageResponse<ExerciseHistoryEntryView> getExerciseHistory(
            Authentication authentication,
            @PathVariable UUID exerciseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return workoutService.getExerciseHistory(currentPrincipal(authentication), exerciseId, from, to, page, size);
    }

    @Operation(summary = "Get current personal records for an accessible exercise")
    @GetMapping("/exercises/{exerciseId}/records")
    public ExerciseRecordsView getExerciseRecords(Authentication authentication,
                                                  @PathVariable UUID exerciseId) {
        return workoutService.getExerciseRecords(currentPrincipal(authentication), exerciseId);
    }

    @Operation(summary = "Export workout data for the current user")
    @GetMapping("/export")
    public WorkoutExportView exportWorkoutData(Authentication authentication) {
        return workoutService.exportWorkoutData(currentPrincipal(authentication));
    }

    @Operation(summary = "Update a custom exercise")
    @PutMapping("/exercises/{exerciseId}")
    public ExerciseView updateExercise(Authentication authentication,
                                       @PathVariable UUID exerciseId,
                                       @Valid @RequestBody UpdateExerciseRequest request) {
        return exerciseService.updateCustomExercise(currentPrincipal(authentication), exerciseId, request);
    }

    @Operation(summary = "Upload a video for a custom exercise")
    @PostMapping(value = "/exercises/{exerciseId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExerciseView uploadExerciseVideo(Authentication authentication,
                                            @PathVariable UUID exerciseId,
                                            @RequestPart("file") MultipartFile file) {
        return exerciseService.uploadExerciseVideo(currentPrincipal(authentication), exerciseId, file);
    }

    @Operation(summary = "Upload an image for a custom exercise")
    @PostMapping(value = "/exercises/{exerciseId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExerciseView uploadExerciseImage(Authentication authentication,
                                            @PathVariable UUID exerciseId,
                                            @RequestPart("file") MultipartFile file) {
        return exerciseService.uploadExerciseImage(currentPrincipal(authentication), exerciseId, file);
    }

    @Operation(summary = "Archive a custom exercise")
    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveExercise(Authentication authentication,
                                @PathVariable UUID exerciseId) {
        exerciseService.archiveCustomExercise(currentPrincipal(authentication), exerciseId);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanView createPlan(Authentication authentication,
                                      @Valid @RequestBody CreateWorkoutPlanRequest request) {
        return workoutService.createPlan(currentPrincipal(authentication), request);
    }

    @GetMapping("/plans")
    public PageResponse<WorkoutPlanView> listPlans(
            Authentication authentication,
            @RequestParam(required = false) WorkoutPlanStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return workoutService.listPlans(currentPrincipal(authentication), status, page, size);
    }

    @Operation(summary = "Get the current active workout plan")
    @GetMapping("/plans/active")
    public WorkoutPlanView getActivePlan(Authentication authentication) {
        return workoutService.getActivePlan(currentPrincipal(authentication));
    }

    @GetMapping("/plans/{planId}")
    public WorkoutPlanView getPlan(Authentication authentication,
                                   @PathVariable UUID planId) {
        return workoutService.getPlan(currentPrincipal(authentication), planId);
    }

    @Operation(summary = "Duplicate a workout plan")
    @PostMapping("/plans/{planId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanView duplicatePlan(Authentication authentication,
                                         @PathVariable UUID planId) {
        return workoutService.duplicatePlan(currentPrincipal(authentication), planId);
    }

    @PutMapping("/plans/{planId}")
    public WorkoutPlanView updatePlan(Authentication authentication,
                                      @PathVariable UUID planId,
                                      @Valid @RequestBody UpdateWorkoutPlanRequest request) {
        return workoutService.updatePlan(currentPrincipal(authentication), planId, request);
    }

    @DeleteMapping("/plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archivePlan(Authentication authentication,
                            @PathVariable UUID planId) {
        workoutService.archivePlan(currentPrincipal(authentication), planId);
    }

    @Operation(summary = "Activate a workout plan")
    @PostMapping("/plans/{planId}/activate")
    public WorkoutPlanView activatePlan(Authentication authentication,
                                        @PathVariable UUID planId) {
        return workoutService.activatePlan(currentPrincipal(authentication), planId);
    }

    @Operation(summary = "Start a workout session from a workout plan")
    @PostMapping("/plans/{planId}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSessionView startPlan(Authentication authentication,
                                        @PathVariable UUID planId) {
        return workoutService.startPlan(currentPrincipal(authentication), planId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSessionView createSession(Authentication authentication,
                                            @Valid @RequestBody UpsertWorkoutSessionRequest request) {
        return workoutService.createSession(currentPrincipal(authentication), request);
    }

    @Operation(summary = "Import externally sourced workout sessions")
    @PostMapping("/sessions/import")
    public ImportWorkoutSessionsResponse importSessions(Authentication authentication,
                                                        @Valid @RequestBody ImportWorkoutSessionsRequest request) {
        return workoutService.importSessions(currentPrincipal(authentication), request);
    }

    @GetMapping("/sessions")
    public PageResponse<WorkoutSessionView> listSessions(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) WorkoutSessionStatus status,
            @RequestParam(required = false) WorkoutType workoutType,
            @RequestParam(required = false) @Min(0) Integer minDurationMinutes,
            @RequestParam(required = false) @Min(0) Integer maxDurationMinutes,
            @RequestParam(required = false) UUID planId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return workoutService.listSessions(
                currentPrincipal(authentication),
                from,
                to,
                status,
                workoutType,
                minDurationMinutes,
                maxDurationMinutes,
                planId,
                page,
                size);
    }

    @GetMapping("/sessions/latest")
    public WorkoutSessionView latestSession(Authentication authentication) {
        return workoutService.getLatestSession(currentPrincipal(authentication));
    }

    @Operation(summary = "Repeat the latest completed workout session")
    @PostMapping("/sessions/repeat-latest")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSessionView repeatLatestSession(Authentication authentication) {
        return workoutService.repeatLatestSession(currentPrincipal(authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    public WorkoutSessionView getSession(Authentication authentication,
                                         @PathVariable UUID sessionId) {
        return workoutService.getSession(currentPrincipal(authentication), sessionId);
    }

    @Operation(summary = "Get guided workout resume progress and next set")
    @GetMapping("/sessions/{sessionId}/resume")
    public WorkoutSessionResumeView resumeSession(Authentication authentication,
                                                  @PathVariable UUID sessionId) {
        return workoutService.resumeSession(currentPrincipal(authentication), sessionId);
    }

    @Operation(summary = "Repeat a workout session")
    @PostMapping("/sessions/{sessionId}/repeat")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSessionView repeatSession(Authentication authentication,
                                            @PathVariable UUID sessionId) {
        return workoutService.repeatSession(currentPrincipal(authentication), sessionId);
    }

    @PutMapping("/sessions/{sessionId}")
    public WorkoutSessionView updateSession(Authentication authentication,
                                            @PathVariable UUID sessionId,
                                            @Valid @RequestBody UpsertWorkoutSessionRequest request) {
        return workoutService.updateSession(currentPrincipal(authentication), sessionId, request);
    }

    @Operation(summary = "Complete a workout session")
    @PostMapping("/sessions/{sessionId}/complete")
    public WorkoutSessionView completeSession(Authentication authentication,
                                              @PathVariable UUID sessionId,
                                              @Valid @RequestBody(required = false) CompleteWorkoutSessionRequest request) {
        return workoutService.completeSession(currentPrincipal(authentication), sessionId, request);
    }

    @Operation(summary = "Cancel a workout session")
    @PostMapping("/sessions/{sessionId}/cancel")
    public WorkoutSessionView cancelSession(Authentication authentication,
                                            @PathVariable UUID sessionId) {
        return workoutService.cancelSession(currentPrincipal(authentication), sessionId);
    }

    @Operation(summary = "Append or replace one exercise log in an editable workout session")
    @PostMapping("/sessions/{sessionId}/exercises")
    public WorkoutSessionView appendOrReplaceExercise(Authentication authentication,
                                                      @PathVariable UUID sessionId,
                                                      @Valid @RequestBody WorkoutExerciseLogInput request) {
        return workoutService.appendOrReplaceExercise(currentPrincipal(authentication), sessionId, request);
    }

    @Operation(summary = "Upsert one set in an editable workout session exercise log")
    @PutMapping("/sessions/{sessionId}/exercises/{exerciseLogId}/sets/{setNumber}")
    public WorkoutSessionView upsertSet(Authentication authentication,
                                        @PathVariable UUID sessionId,
                                        @PathVariable UUID exerciseLogId,
                                        @PathVariable @Min(1) int setNumber,
                                        @Valid @RequestBody UpsertWorkoutExerciseSetRequest request) {
        return workoutService.upsertSet(currentPrincipal(authentication), sessionId, exerciseLogId, setNumber, request);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(Authentication authentication,
                              @PathVariable UUID sessionId) {
        workoutService.deleteSession(currentPrincipal(authentication), sessionId);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
