package com.fitsync.workout.controller;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutPlanView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/plans/{planId}")
    public WorkoutPlanView getPlan(Authentication authentication,
                                   @PathVariable UUID planId) {
        return workoutService.getPlan(currentPrincipal(authentication), planId);
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

    @GetMapping("/sessions")
    public PageResponse<WorkoutSessionView> listSessions(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) WorkoutSessionStatus status,
            @RequestParam(required = false) UUID planId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return workoutService.listSessions(currentPrincipal(authentication), from, to, status, planId, page, size);
    }

    @GetMapping("/sessions/latest")
    public WorkoutSessionView latestSession(Authentication authentication) {
        return workoutService.getLatestSession(currentPrincipal(authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    public WorkoutSessionView getSession(Authentication authentication,
                                         @PathVariable UUID sessionId) {
        return workoutService.getSession(currentPrincipal(authentication), sessionId);
    }

    @PutMapping("/sessions/{sessionId}")
    public WorkoutSessionView updateSession(Authentication authentication,
                                            @PathVariable UUID sessionId,
                                            @Valid @RequestBody UpsertWorkoutSessionRequest request) {
        return workoutService.updateSession(currentPrincipal(authentication), sessionId, request);
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
