package com.fitsync.workout.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.ActiveWorkoutSessionView;
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordsView;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExportView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionResumeView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.response.PageResponse;

public interface WorkoutService {

    WorkoutPlanView createPlan(CurrentUserPrincipal currentUser, CreateWorkoutPlanRequest request);

    PageResponse<WorkoutPlanView> listPlans(CurrentUserPrincipal currentUser,
                                            WorkoutPlanStatus status,
                                            int page,
                                            int size);

    WorkoutPlanView getPlan(CurrentUserPrincipal currentUser, UUID planId);

    WorkoutPlanView getActivePlan(CurrentUserPrincipal currentUser);

    WorkoutPlanView duplicatePlan(CurrentUserPrincipal currentUser, UUID planId);

    PageResponse<ExerciseHistoryEntryView> getExerciseHistory(CurrentUserPrincipal currentUser,
                                                              UUID exerciseId,
                                                              Instant from,
                                                              Instant to,
                                                              int page,
                                                              int size);

    ExerciseRecordsView getExerciseRecords(CurrentUserPrincipal currentUser, UUID exerciseId);

    WorkoutExportView exportWorkoutData(CurrentUserPrincipal currentUser);

    WorkoutPlanView updatePlan(CurrentUserPrincipal currentUser, UUID planId, UpdateWorkoutPlanRequest request);

    void archivePlan(CurrentUserPrincipal currentUser, UUID planId);

    WorkoutPlanView activatePlan(CurrentUserPrincipal currentUser, UUID planId);

    /** Starts a session from a flat plan; equivalent to {@code startPlan(currentUser, planId, null, "UTC")}. */
    WorkoutSessionView startPlan(CurrentUserPrincipal currentUser, UUID planId);

    WorkoutSessionView startPlan(CurrentUserPrincipal currentUser, UUID planId, UUID planDayId, String timezone);

    WorkoutSessionView createSession(CurrentUserPrincipal currentUser, UpsertWorkoutSessionRequest request);

    ImportWorkoutSessionsResponse importSessions(CurrentUserPrincipal currentUser, ImportWorkoutSessionsRequest request);

    PageResponse<WorkoutSessionView> listSessions(CurrentUserPrincipal currentUser,
                                                  Instant from,
                                                  Instant to,
                                                  WorkoutSessionStatus status,
                                                  WorkoutType workoutType,
                                                  Integer minDurationMinutes,
                                                  Integer maxDurationMinutes,
                                                  UUID planId,
                                                  String q,
                                                  int page,
                                                  int size);

    WorkoutSessionView getLatestSession(CurrentUserPrincipal currentUser);

    /**
     * The caller's workout in progress with its resume state, or empty when there is none. Closes
     * the caller's left workouts first, so a forgotten session is never reported as current.
     */
    Optional<ActiveWorkoutSessionView> getActiveSession(CurrentUserPrincipal currentUser);

    /** Closes the in-progress workouts of every user that were left. Called by the scheduled sweep. */
    StaleSessionSweepResult closeStaleSessions();

    WorkoutSessionView getSession(CurrentUserPrincipal currentUser, UUID sessionId);

    WorkoutSessionResumeView resumeSession(CurrentUserPrincipal currentUser, UUID sessionId);

    WorkoutSessionView repeatSession(CurrentUserPrincipal currentUser, UUID sessionId);

    WorkoutSessionView repeatLatestSession(CurrentUserPrincipal currentUser);

    WorkoutSessionView updateSession(CurrentUserPrincipal currentUser, UUID sessionId, UpsertWorkoutSessionRequest request);

    WorkoutSessionView completeSession(CurrentUserPrincipal currentUser, UUID sessionId, CompleteWorkoutSessionRequest request);

    WorkoutSessionView cancelSession(CurrentUserPrincipal currentUser, UUID sessionId);

    WorkoutSessionView appendOrReplaceExercise(CurrentUserPrincipal currentUser, UUID sessionId, WorkoutExerciseLogInput request);

    WorkoutSessionView upsertSet(CurrentUserPrincipal currentUser,
                                 UUID sessionId,
                                 UUID exerciseLogId,
                                 int setNumber,
                                 UpsertWorkoutExerciseSetRequest request);

    void deleteSession(CurrentUserPrincipal currentUser, UUID sessionId);
}
