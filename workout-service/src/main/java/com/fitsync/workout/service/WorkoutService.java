package com.fitsync.workout.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutPlanView;
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

    WorkoutPlanView updatePlan(CurrentUserPrincipal currentUser, UUID planId, UpdateWorkoutPlanRequest request);

    void archivePlan(CurrentUserPrincipal currentUser, UUID planId);

    WorkoutSessionView createSession(CurrentUserPrincipal currentUser, UpsertWorkoutSessionRequest request);

    PageResponse<WorkoutSessionView> listSessions(CurrentUserPrincipal currentUser,
                                                  Instant from,
                                                  Instant to,
                                                  WorkoutSessionStatus status,
                                                  UUID planId,
                                                  int page,
                                                  int size);

    WorkoutSessionView getLatestSession(CurrentUserPrincipal currentUser);

    WorkoutSessionView getSession(CurrentUserPrincipal currentUser, UUID sessionId);

    WorkoutSessionView updateSession(CurrentUserPrincipal currentUser, UUID sessionId, UpsertWorkoutSessionRequest request);

    void deleteSession(CurrentUserPrincipal currentUser, UUID sessionId);
}
