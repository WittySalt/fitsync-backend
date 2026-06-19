package com.fitsync.workout.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseLogView;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetView;
import com.fitsync.workout.dto.WorkoutPlanExerciseView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.entity.WorkoutPlanExerciseEntity;
import com.fitsync.workout.entity.WorkoutPlanExerciseSetTargetEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.messaging.DomainEventPublisher;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutServiceImpl implements WorkoutService {

    private static final String SOURCE = "workout-service";

    private final String workoutEventsTopic;
    private final DomainEventPublisher domainEventPublisher;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    public WorkoutServiceImpl(@Value("${fitsync.kafka.topics.workout-events:workout-events}") String workoutEventsTopic,
                              DomainEventPublisher domainEventPublisher,
                              ExerciseRepository exerciseRepository,
                              WorkoutPlanRepository workoutPlanRepository,
                              WorkoutSessionRepository workoutSessionRepository) {
        this.workoutEventsTopic = workoutEventsTopic;
        this.domainEventPublisher = domainEventPublisher;
        this.exerciseRepository = exerciseRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutSessionRepository = workoutSessionRepository;
    }

    @Override
    @Transactional
    public WorkoutPlanView createPlan(CurrentUserPrincipal currentUser, CreateWorkoutPlanRequest request) {
        validatePlanExercises(request.exercises());
        WorkoutPlanEntity entity = new WorkoutPlanEntity();
        entity.setPlanId(UUID.randomUUID());
        entity.setUserId(currentUser.userId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setGoalFocus(request.goalFocus());
        entity.setWorkoutType(request.workoutType());
        entity.setDifficulty(request.difficulty());
        entity.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        entity.setStatus(WorkoutPlanStatus.DRAFT);
        replacePlanExercises(currentUser.userId(), entity, request.exercises());
        return toPlanView(workoutPlanRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkoutPlanView> listPlans(CurrentUserPrincipal currentUser,
                                                   WorkoutPlanStatus status,
                                                   int page,
                                                   int size) {
        return toPageResponse(workoutPlanRepository.findByUserIdAndOptionalStatus(
                        currentUser.userId(),
                        status,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateTimeUpdated")))
                .map(this::toPlanView));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutPlanView getPlan(CurrentUserPrincipal currentUser, UUID planId) {
        return toPlanView(findPlan(currentUser.userId(), planId));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutPlanView getActivePlan(CurrentUserPrincipal currentUser) {
        return workoutPlanRepository.findByUserIdAndStatusOrderByDateTimeUpdatedDesc(
                        currentUser.userId(),
                        WorkoutPlanStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(this::toPlanView)
                .orElseThrow(() -> new ResourceNotFoundException("Active workout plan not found"));
    }

    @Override
    @Transactional
    public WorkoutPlanView updatePlan(CurrentUserPrincipal currentUser, UUID planId, UpdateWorkoutPlanRequest request) {
        validatePlanExercises(request.exercises());
        WorkoutPlanEntity entity = findPlan(currentUser.userId(), planId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setGoalFocus(request.goalFocus());
        entity.setWorkoutType(request.workoutType());
        entity.setDifficulty(request.difficulty());
        entity.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        replacePlanExercises(currentUser.userId(), entity, request.exercises());
        return toPlanView(workoutPlanRepository.save(entity));
    }

    @Override
    @Transactional
    public void archivePlan(CurrentUserPrincipal currentUser, UUID planId) {
        WorkoutPlanEntity entity = findPlan(currentUser.userId(), planId);
        entity.setStatus(WorkoutPlanStatus.ARCHIVED);
        workoutPlanRepository.save(entity);
    }

    @Override
    @Transactional
    public WorkoutPlanView activatePlan(CurrentUserPrincipal currentUser, UUID planId) {
        UUID userId = currentUser.userId();
        WorkoutPlanEntity plan = findPlan(userId, planId);
        for (WorkoutPlanEntity activePlan : workoutPlanRepository.findByUserIdAndStatus(userId, WorkoutPlanStatus.ACTIVE)) {
            if (!activePlan.getPlanId().equals(plan.getPlanId())) {
                activePlan.setStatus(WorkoutPlanStatus.DRAFT);
            }
        }
        plan.setStatus(WorkoutPlanStatus.ACTIVE);
        return toPlanView(workoutPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public WorkoutSessionView startPlan(CurrentUserPrincipal currentUser, UUID planId) {
        WorkoutPlanEntity plan = findPlan(currentUser.userId(), planId);
        if (plan.getStatus() == WorkoutPlanStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archived workout plan cannot be used for a session");
        }

        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(currentUser.userId());
        session.setPlanId(plan.getPlanId());
        session.setName(plan.getName());
        session.setWorkoutType(plan.getWorkoutType());
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        session.setSource(WorkoutSource.PLAN);
        session.setStartedAt(Instant.now());

        for (WorkoutPlanExerciseEntity planExercise : plan.getExercises()) {
            session.getExercises().add(toSeededExerciseLog(session, planExercise));
        }

        return toSessionView(workoutSessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionView createSession(CurrentUserPrincipal currentUser, UpsertWorkoutSessionRequest request) {
        validateSessionRequest(currentUser.userId(), request);
        WorkoutSessionEntity entity = new WorkoutSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(currentUser.userId());
        applySessionRequest(currentUser.userId(), entity, request);
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        if (saved.getStatus() == WorkoutSessionStatus.COMPLETED) {
            publishWorkoutLogged(saved);
        }
        return toSessionView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkoutSessionView> listSessions(CurrentUserPrincipal currentUser,
                                                         Instant from,
                                                         Instant to,
                                                         WorkoutSessionStatus status,
                                                         UUID planId,
                                                         int page,
                                                         int size) {
        validateTimeRange(from, to);
        return toPageResponse(workoutSessionRepository.findHistory(
                        currentUser.userId(),
                        from,
                        to,
                        status,
                        planId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")))
                .map(this::toSessionView));
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSessionView getLatestSession(CurrentUserPrincipal currentUser) {
        return workoutSessionRepository.findTopByUserIdOrderByStartedAtDesc(currentUser.userId())
                .map(this::toSessionView)
                .orElseThrow(() -> new ResourceNotFoundException("Workout session not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSessionView getSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        return toSessionView(findSession(currentUser.userId(), sessionId));
    }

    @Override
    @Transactional
    public WorkoutSessionView updateSession(CurrentUserPrincipal currentUser,
                                            UUID sessionId,
                                            UpsertWorkoutSessionRequest request) {
        validateSessionRequest(currentUser.userId(), request);
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        WorkoutSessionStatus previousStatus = entity.getStatus();
        applySessionRequest(currentUser.userId(), entity, request);
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        if (saved.getStatus() == WorkoutSessionStatus.COMPLETED && previousStatus != WorkoutSessionStatus.COMPLETED) {
            publishWorkoutLogged(saved);
        } else if (saved.getStatus() == WorkoutSessionStatus.COMPLETED) {
            publishWorkoutUpdated(saved);
        }
        return toSessionView(saved);
    }

    @Override
    @Transactional
    public void deleteSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        boolean publishDelete = entity.getStatus() == WorkoutSessionStatus.COMPLETED;
        workoutSessionRepository.delete(entity);
        if (publishDelete) {
            domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", sessionId.toString(), new FitSyncDomainEvent<>(
                    UUID.randomUUID(),
                    "WorkoutDeleted",
                    1,
                    Instant.now(),
                    SOURCE,
                    currentUser.userId(),
                    new WorkoutDeletedPayload(entity.getSessionId(), entity.getPlanId(), Instant.now())));
        }
    }

    private WorkoutPlanEntity findPlan(UUID userId, UUID planId) {
        return workoutPlanRepository.findByPlanIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout plan not found"));
    }

    private WorkoutSessionEntity findSession(UUID userId, UUID sessionId) {
        return workoutSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout session not found"));
    }

    private void applySessionRequest(UUID userId,
                                     WorkoutSessionEntity entity,
                                     UpsertWorkoutSessionRequest request) {
        entity.setPlanId(request.planId());
        entity.setName(request.name());
        entity.setWorkoutType(request.workoutType());
        entity.setStatus(request.status());
        entity.setSource(request.source());
        entity.setStartedAt(request.startedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setDurationMinutes(request.durationMinutes());
        entity.setCaloriesBurned(request.caloriesBurned());
        entity.setPerceivedExertion(request.perceivedExertion());
        entity.setNotes(request.notes());
        replaceSessionExercises(userId, entity, request.exercises());
    }

    private void replacePlanExercises(UUID userId, WorkoutPlanEntity plan, List<WorkoutPlanExerciseInput> inputs) {
        plan.getExercises().clear();
        if (inputs == null) {
            return;
        }
        for (WorkoutPlanExerciseInput input : inputs.stream().sorted(java.util.Comparator.comparing(WorkoutPlanExerciseInput::sequence)).toList()) {
            WorkoutPlanExerciseEntity exercise = new WorkoutPlanExerciseEntity();
            ExerciseEntity sourceExercise = findAccessibleExercise(userId, input.exerciseId());
            exercise.setPlanExerciseId(UUID.randomUUID());
            exercise.setPlan(plan);
            exercise.setExercise(sourceExercise);
            exercise.setExerciseNameSnapshot(sourceExercise.getName());
            exercise.setSequence(input.sequence());
            exercise.setTargetSets(input.targetSets());
            exercise.setTargetReps(input.targetReps());
            exercise.setTargetWeightKg(input.targetWeightKg());
            exercise.setTargetDurationSeconds(input.targetDurationSeconds());
            exercise.setTargetDistanceKm(input.targetDistanceKm());
            exercise.setRestSeconds(input.restSeconds());
            exercise.setNotes(input.notes());
            replaceSetTargets(exercise, input.setTargets());
            plan.getExercises().add(exercise);
        }
    }

    private void replaceSetTargets(WorkoutPlanExerciseEntity exercise,
                                   List<WorkoutPlanExerciseSetTargetInput> inputs) {
        exercise.getSetTargets().clear();
        if (inputs == null) {
            return;
        }
        for (WorkoutPlanExerciseSetTargetInput input : inputs.stream()
                .sorted(java.util.Comparator.comparing(WorkoutPlanExerciseSetTargetInput::setNumber))
                .toList()) {
            WorkoutPlanExerciseSetTargetEntity setTarget = new WorkoutPlanExerciseSetTargetEntity();
            setTarget.setSetTargetId(UUID.randomUUID());
            setTarget.setPlanExercise(exercise);
            setTarget.setSetNumber(input.setNumber());
            setTarget.setTargetReps(input.targetReps());
            setTarget.setTargetWeightKg(input.targetWeightKg());
            setTarget.setTargetDurationSeconds(input.targetDurationSeconds());
            setTarget.setTargetDistanceKm(input.targetDistanceKm());
            setTarget.setRestSeconds(input.restSeconds());
            exercise.getSetTargets().add(setTarget);
        }
    }

    private void replaceSessionExercises(UUID userId, WorkoutSessionEntity session, List<WorkoutExerciseLogInput> inputs) {
        session.getExercises().clear();
        if (inputs == null) {
            return;
        }
        for (WorkoutExerciseLogInput input : inputs.stream().sorted(java.util.Comparator.comparing(WorkoutExerciseLogInput::sequence)).toList()) {
            WorkoutExerciseLogEntity exercise = new WorkoutExerciseLogEntity();
            ExerciseEntity sourceExercise = findAccessibleExercise(userId, input.exerciseId());
            exercise.setExerciseLogId(UUID.randomUUID());
            exercise.setSession(session);
            exercise.setExercise(sourceExercise);
            exercise.setExerciseNameSnapshot(sourceExercise.getName());
            exercise.setSequence(input.sequence());
            exercise.setPerformedSets(input.performedSets());
            exercise.setPerformedReps(input.performedReps());
            exercise.setTotalVolumeKg(input.totalVolumeKg());
            exercise.setDistanceKm(input.distanceKm());
            exercise.setDurationSeconds(input.durationSeconds());
            exercise.setAverageHeartRate(input.averageHeartRate());
            exercise.setMaxHeartRate(input.maxHeartRate());
            exercise.setNotes(input.notes());
            replaceSetLogs(exercise, input.sets());
            session.getExercises().add(exercise);
        }
    }

    private void replaceSetLogs(WorkoutExerciseLogEntity exercise,
                                List<WorkoutExerciseSetLogInput> inputs) {
        exercise.getSets().clear();
        if (inputs == null) {
            return;
        }
        for (WorkoutExerciseSetLogInput input : inputs.stream()
                .sorted(java.util.Comparator.comparing(WorkoutExerciseSetLogInput::setNumber))
                .toList()) {
            WorkoutExerciseSetLogEntity setLog = new WorkoutExerciseSetLogEntity();
            setLog.setSetLogId(UUID.randomUUID());
            setLog.setExerciseLog(exercise);
            setLog.setSetNumber(input.setNumber());
            setLog.setReps(input.reps());
            setLog.setWeightKg(input.weightKg());
            setLog.setDurationSeconds(input.durationSeconds());
            setLog.setDistanceKm(input.distanceKm());
            setLog.setRestSeconds(input.restSeconds());
            setLog.setPerceivedExertion(input.perceivedExertion());
            setLog.setNotes(input.notes());
            exercise.getSets().add(setLog);
        }
    }

    private WorkoutExerciseLogEntity toSeededExerciseLog(WorkoutSessionEntity session,
                                                         WorkoutPlanExerciseEntity planExercise) {
        WorkoutExerciseLogEntity exercise = new WorkoutExerciseLogEntity();
        exercise.setExerciseLogId(UUID.randomUUID());
        exercise.setSession(session);
        exercise.setExercise(planExercise.getExercise());
        exercise.setExerciseNameSnapshot(planExercise.getExerciseNameSnapshot());
        exercise.setSequence(planExercise.getSequence());
        exercise.setNotes(planExercise.getNotes());

        for (WorkoutPlanExerciseSetTargetEntity setTarget : planExercise.getSetTargets()) {
            exercise.getSets().add(toSeededSetLog(exercise, setTarget));
        }
        return exercise;
    }

    private WorkoutExerciseSetLogEntity toSeededSetLog(WorkoutExerciseLogEntity exercise,
                                                       WorkoutPlanExerciseSetTargetEntity setTarget) {
        WorkoutExerciseSetLogEntity setLog = new WorkoutExerciseSetLogEntity();
        setLog.setSetLogId(UUID.randomUUID());
        setLog.setExerciseLog(exercise);
        setLog.setSetNumber(setTarget.getSetNumber());
        setLog.setReps(setTarget.getTargetReps());
        setLog.setWeightKg(setTarget.getTargetWeightKg());
        setLog.setDurationSeconds(setTarget.getTargetDurationSeconds());
        setLog.setDistanceKm(setTarget.getTargetDistanceKm());
        setLog.setRestSeconds(setTarget.getRestSeconds());
        return setLog;
    }

    private ExerciseEntity findAccessibleExercise(UUID userId, UUID exerciseId) {
        return exerciseRepository.findAccessibleById(exerciseId, userId, ExerciseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
    }

    private void validateSessionRequest(UUID userId, UpsertWorkoutSessionRequest request) {
        validateTimeRange(request.startedAt(), request.completedAt());
        validateSessionTiming(request);
        validateExerciseLogs(request.exercises());
        if (request.planId() != null) {
            WorkoutPlanEntity plan = findPlan(userId, request.planId());
            if (plan.getStatus() == WorkoutPlanStatus.ARCHIVED) {
                throw new IllegalArgumentException("Archived workout plan cannot be used for a session");
            }
        }
    }

    private void validateSessionTiming(UpsertWorkoutSessionRequest request) {
        if (request.status() == WorkoutSessionStatus.COMPLETED && request.completedAt() == null) {
            throw new IllegalArgumentException("completedAt is required when status is COMPLETED");
        }
        if (request.status() != WorkoutSessionStatus.COMPLETED && request.completedAt() != null) {
            throw new IllegalArgumentException("completedAt is only allowed when status is COMPLETED");
        }
    }

    private void validatePlanExercises(List<WorkoutPlanExerciseInput> exercises) {
        validateUniqueNumbers(
                exercises == null ? List.of() : exercises.stream().map(WorkoutPlanExerciseInput::sequence).toList(),
                "Exercise sequence values must be unique within the request");
        if (exercises != null) {
            exercises.forEach(exercise -> validateUniqueNumbers(
                    exercise.setTargets() == null ? List.of() : exercise.setTargets().stream()
                            .map(WorkoutPlanExerciseSetTargetInput::setNumber)
                            .toList(),
                    "Set target numbers must be unique within a plan exercise"));
        }
    }

    private void validateExerciseLogs(List<WorkoutExerciseLogInput> exercises) {
        validateUniqueNumbers(
                exercises == null ? List.of() : exercises.stream().map(WorkoutExerciseLogInput::sequence).toList(),
                "Exercise sequence values must be unique within the request");
        if (exercises != null) {
            exercises.forEach(exercise -> validateUniqueNumbers(
                    exercise.sets() == null ? List.of() : exercise.sets().stream()
                            .map(WorkoutExerciseSetLogInput::setNumber)
                            .toList(),
                    "Set numbers must be unique within an exercise log"));
        }
    }

    private void validateUniqueNumbers(List<Integer> numbers, String message) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Integer number : numbers) {
            if (!seen.add(number)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    private void publishWorkoutLogged(WorkoutSessionEntity session) {
        domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", session.getSessionId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                Instant.now(),
                SOURCE,
                session.getUserId(),
                sessionPayload(session)));
    }

    private void publishWorkoutUpdated(WorkoutSessionEntity session) {
        WorkoutLoggedPayload payload = sessionPayload(session);
        domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", session.getSessionId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutUpdated",
                1,
                Instant.now(),
                SOURCE,
                session.getUserId(),
                new WorkoutUpdatedPayload(
                        payload.sessionId(),
                        payload.planId(),
                        payload.workoutType(),
                        payload.status(),
                        payload.startedAt(),
                        payload.completedAt(),
                        payload.durationMinutes(),
                        payload.caloriesBurned(),
                        payload.exerciseCount(),
                        payload.source())));
    }

    private String workoutEventsTopic() {
        return workoutEventsTopic;
    }

    private WorkoutLoggedPayload sessionPayload(WorkoutSessionEntity session) {
        return new WorkoutLoggedPayload(
                session.getSessionId(),
                session.getPlanId(),
                session.getWorkoutType().name(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getDurationMinutes(),
                session.getCaloriesBurned(),
                session.getExercises().size(),
                session.getSource().name());
    }

    private WorkoutPlanView toPlanView(WorkoutPlanEntity entity) {
        return new WorkoutPlanView(
                entity.getPlanId(),
                entity.getName(),
                entity.getDescription(),
                entity.getGoalFocus(),
                entity.getWorkoutType(),
                entity.getDifficulty(),
                entity.getEstimatedDurationMinutes(),
                entity.getStatus(),
                entity.getExercises().stream()
                        .map(exercise -> new WorkoutPlanExerciseView(
                                exercise.getPlanExerciseId(),
                                exercise.getExercise().getExerciseId(),
                                exercise.getExerciseNameSnapshot(),
                                exercise.getSequence(),
                                exercise.getTargetSets(),
                                exercise.getTargetReps(),
                                exercise.getTargetWeightKg(),
                                exercise.getTargetDurationSeconds(),
                                exercise.getTargetDistanceKm(),
                                exercise.getRestSeconds(),
                                exercise.getNotes(),
                                exercise.getSetTargets().stream()
                                        .map(setTarget -> new WorkoutPlanExerciseSetTargetView(
                                                setTarget.getSetTargetId(),
                                                setTarget.getSetNumber(),
                                                setTarget.getTargetReps(),
                                                setTarget.getTargetWeightKg(),
                                                setTarget.getTargetDurationSeconds(),
                                                setTarget.getTargetDistanceKm(),
                                                setTarget.getRestSeconds()))
                                        .toList()))
                        .toList(),
                entity.getDateTimeCreated(),
                entity.getDateTimeUpdated());
    }

    private WorkoutSessionView toSessionView(WorkoutSessionEntity entity) {
        List<WorkoutExerciseLogView> exercises = entity.getExercises().stream()
                .map(exercise -> new WorkoutExerciseLogView(
                        exercise.getExerciseLogId(),
                        exercise.getExercise().getExerciseId(),
                        exercise.getExerciseNameSnapshot(),
                        exercise.getSequence(),
                        exercise.getPerformedSets(),
                        exercise.getPerformedReps(),
                        exercise.getTotalVolumeKg(),
                        exercise.getDistanceKm(),
                        exercise.getDurationSeconds(),
                        exercise.getAverageHeartRate(),
                        exercise.getMaxHeartRate(),
                        exercise.getNotes(),
                        exercise.getSets().stream()
                                .map(setLog -> new WorkoutExerciseSetLogView(
                                        setLog.getSetLogId(),
                                        setLog.getSetNumber(),
                                        setLog.getReps(),
                                        setLog.getWeightKg(),
                                        setLog.getDurationSeconds(),
                                        setLog.getDistanceKm(),
                                        setLog.getRestSeconds(),
                                        setLog.getPerceivedExertion(),
                                        setLog.getNotes()))
                                .toList()))
                .toList();
        return new WorkoutSessionView(
                entity.getSessionId(),
                entity.getPlanId(),
                entity.getName(),
                entity.getWorkoutType(),
                entity.getStatus(),
                entity.getSource(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getDurationMinutes(),
                entity.getCaloriesBurned(),
                entity.getPerceivedExertion(),
                entity.getNotes(),
                exercises.size(),
                exercises,
                entity.getDateTimeCreated(),
                entity.getDateTimeUpdated());
    }
}
