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
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanDraftRequest;
import com.fitsync.workout.dto.PlanProposalRequest;
import com.fitsync.workout.dto.SessionEntryUpdateRequest;
import com.fitsync.workout.dto.SetRestRequest;
import com.fitsync.workout.dto.SessionEffortRequest;
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

    /** What a plan being built trains: hard sets a week per group and the editor's notes. */
    PlanCoverageView previewCoverage(CurrentUserPrincipal currentUser, PlanDraftRequest request);

    /** A default week built from the profile the client sends, the person's habit days and the catalogue. */
    WorkoutPlanView proposePlan(CurrentUserPrincipal currentUser, PlanProposalRequest request, String timezone);

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

    /** Creates a session whose entries are dated on its start's local date in the given zone. */
    WorkoutSessionView createSession(CurrentUserPrincipal currentUser, UpsertWorkoutSessionRequest request, String timezone);

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

    /** Counts a cancelled session that has a done set: it is completed at its last set, with its events. */
    WorkoutSessionView countSession(CurrentUserPrincipal currentUser, UUID sessionId);

    /** The effort the person chose for a completed session, replacing the estimate. */
    WorkoutSessionView updateEffort(CurrentUserPrincipal currentUser, UUID sessionId, SessionEffortRequest request);

    /** The rest the person actually took after a done set of a session in progress. */
    WorkoutSessionView recordRestTaken(CurrentUserPrincipal currentUser, UUID sessionId, UUID exerciseLogId,
                                       int setNumber, SetRestRequest request);

    WorkoutSessionView appendOrReplaceExercise(CurrentUserPrincipal currentUser, UUID sessionId, WorkoutExerciseLogInput request);

    WorkoutSessionView upsertSet(CurrentUserPrincipal currentUser,
                                 UUID sessionId,
                                 UUID exerciseLogId,
                                 int setNumber,
                                 UpsertWorkoutExerciseSetRequest request);

    /** Gives one entry of a session in progress its actual values: a segment its time, a move one set. */
    WorkoutSessionView updateEntry(CurrentUserPrincipal currentUser, UUID sessionId, UUID entryId,
                                   SessionEntryUpdateRequest request);

    void deleteSession(CurrentUserPrincipal currentUser, UUID sessionId);
}
