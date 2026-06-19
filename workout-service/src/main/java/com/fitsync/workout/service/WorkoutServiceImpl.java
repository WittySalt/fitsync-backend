package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.dto.ExerciseRecordValueView;
import com.fitsync.workout.dto.ExerciseRecordsView;
import com.fitsync.workout.dto.ImportWorkoutSessionRowRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionRowResult;
import com.fitsync.workout.dto.ImportWorkoutSessionRowStatus;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseLogView;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogView;
import com.fitsync.workout.dto.WorkoutExportView;
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
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkoutServiceImpl implements WorkoutService {

    private static final String SOURCE = "workout-service";
    private static final String MAX_WEIGHT_KG = "MAX_WEIGHT_KG";
    private static final String MAX_REPS = "MAX_REPS";
    private static final String MAX_SINGLE_SET_VOLUME_KG = "MAX_SINGLE_SET_VOLUME_KG";
    private static final String ESTIMATED_ONE_REP_MAX_KG = "ESTIMATED_ONE_REP_MAX_KG";
    private static final String BEST_SESSION_VOLUME_KG = "BEST_SESSION_VOLUME_KG";
    private static final String COPY_SUFFIX = " (Copy)";
    private static final int PLAN_NAME_MAX_LENGTH = 120;

    private final String workoutEventsTopic;
    private final int exerciseHistoryMaxRangeDays;
    private final DomainEventPublisher domainEventPublisher;
    private final ExerciseService exerciseService;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final Validator validator;
    private final TransactionTemplate importRowTransactionTemplate;

    public WorkoutServiceImpl(@Value("${fitsync.kafka.topics.workout-events:workout-events}") String workoutEventsTopic,
                              @Value("${fitsync.workout.exercise-history.max-range-days:370}") int exerciseHistoryMaxRangeDays,
                              DomainEventPublisher domainEventPublisher,
                              ExerciseService exerciseService,
                              ExerciseRepository exerciseRepository,
                              WorkoutExerciseLogRepository workoutExerciseLogRepository,
                              WorkoutPlanRepository workoutPlanRepository,
                              WorkoutSessionRepository workoutSessionRepository,
                              Validator validator,
                              PlatformTransactionManager transactionManager) {
        this.workoutEventsTopic = workoutEventsTopic;
        this.exerciseHistoryMaxRangeDays = exerciseHistoryMaxRangeDays;
        this.domainEventPublisher = domainEventPublisher;
        this.exerciseService = exerciseService;
        this.exerciseRepository = exerciseRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.validator = validator;
        this.importRowTransactionTemplate = new TransactionTemplate(transactionManager);
        this.importRowTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
    public WorkoutPlanView duplicatePlan(CurrentUserPrincipal currentUser, UUID planId) {
        WorkoutPlanEntity source = findPlan(currentUser.userId(), planId);
        WorkoutPlanEntity copy = toDuplicatePlan(currentUser.userId(), source);
        return toPlanView(workoutPlanRepository.save(copy));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExerciseHistoryEntryView> getExerciseHistory(CurrentUserPrincipal currentUser,
                                                                     UUID exerciseId,
                                                                     Instant from,
                                                                     Instant to,
                                                                     int page,
                                                                     int size) {
        ExerciseEntity exercise = findAccessibleExercise(currentUser.userId(), exerciseId);
        validateHistoryRange(from, to);
        return toPageResponse(workoutExerciseLogRepository.findExerciseHistory(
                currentUser.userId(),
                exercise.getExerciseId(),
                from,
                to,
                PageRequest.of(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseRecordsView getExerciseRecords(CurrentUserPrincipal currentUser, UUID exerciseId) {
        ExerciseEntity exercise = findAccessibleExercise(currentUser.userId(), exerciseId);
        PersonalRecordSnapshot snapshot = snapshotRows(workoutExerciseLogRepository.findRecordRows(
                currentUser.userId(),
                exercise.getExerciseId(),
                null));
        return toExerciseRecordsView(exercise, snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutExportView exportWorkoutData(CurrentUserPrincipal currentUser) {
        UUID userId = currentUser.userId();
        return new WorkoutExportView(
                1,
                Instant.now(),
                exerciseService.exportCustomExercises(currentUser),
                workoutPlanRepository.findByUserIdOrderByDateTimeUpdatedDesc(userId).stream()
                        .map(this::toPlanView)
                        .toList(),
                workoutSessionRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
                        .map(this::toSessionView)
                        .toList());
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
            publishWorkoutLoggedAndPersonalRecords(saved);
        }
        return toSessionView(saved);
    }

    @Override
    public ImportWorkoutSessionsResponse importSessions(CurrentUserPrincipal currentUser,
                                                        ImportWorkoutSessionsRequest request) {
        List<ImportWorkoutSessionRowResult> results = new ArrayList<>();
        List<ImportWorkoutSessionRowRequest> rows = request.sessions();
        for (int index = 0; index < rows.size(); index++) {
            ImportWorkoutSessionRowRequest row = rows.get(index);
            results.add(importOneRow(currentUser.userId(), index, row));
        }

        int imported = (int) results.stream()
                .filter(result -> result.status() == ImportWorkoutSessionRowStatus.IMPORTED)
                .count();
        int skipped = (int) results.stream()
                .filter(result -> result.status() == ImportWorkoutSessionRowStatus.SKIPPED_DUPLICATE)
                .count();
        int rejected = (int) results.stream()
                .filter(result -> result.status() == ImportWorkoutSessionRowStatus.REJECTED)
                .count();
        return new ImportWorkoutSessionsResponse(imported, skipped, rejected, results);
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
        UUID userId = currentUser.userId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<UUID> sessionIds = workoutSessionRepository.findHistoryIds(
                        userId,
                        from,
                        to,
                        status,
                        planId,
                        pageable);
        if (sessionIds.isEmpty()) {
            return toPageResponse(new PageImpl<>(List.of(), pageable, sessionIds.getTotalElements()));
        }

        Map<UUID, WorkoutSessionEntity> sessionsById = workoutSessionRepository
                .findHistoryPageByIds(userId, sessionIds.getContent())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkoutSessionEntity::getSessionId,
                        session -> session));
        List<WorkoutSessionView> views = sessionIds.getContent().stream()
                .map(sessionsById::get)
                .map(this::toSessionView)
                .toList();
        return toPageResponse(new PageImpl<>(views, pageable, sessionIds.getTotalElements()));
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
    public WorkoutSessionView repeatSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity source = findSession(currentUser.userId(), sessionId);
        WorkoutSessionEntity repeated = toRepeatedSession(currentUser.userId(), source);
        return toSessionView(workoutSessionRepository.save(repeated));
    }

    @Override
    @Transactional
    public WorkoutSessionView repeatLatestSession(CurrentUserPrincipal currentUser) {
        WorkoutSessionEntity source = workoutSessionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(currentUser.userId(), WorkoutSessionStatus.COMPLETED)
                .orElseThrow(() -> new ResourceNotFoundException("Completed workout session not found"));
        WorkoutSessionEntity repeated = toRepeatedSession(currentUser.userId(), source);
        return toSessionView(workoutSessionRepository.save(repeated));
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
            publishWorkoutLoggedAndPersonalRecords(saved);
        } else if (saved.getStatus() == WorkoutSessionStatus.COMPLETED) {
            publishWorkoutUpdated(saved);
        }
        return toSessionView(saved);
    }

    @Override
    @Transactional
    public WorkoutSessionView completeSession(CurrentUserPrincipal currentUser,
                                              UUID sessionId,
                                              CompleteWorkoutSessionRequest request) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        validateTransitionAllowed(entity);
        entity.setStatus(WorkoutSessionStatus.COMPLETED);
        entity.setCompletedAt(request == null || request.completedAt() == null ? Instant.now() : request.completedAt());
        validateSessionTimingAndScalars(
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getDurationMinutes(),
                entity.getCaloriesBurned());
        deriveSessionMetrics(entity);
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        publishWorkoutLoggedAndPersonalRecords(saved);
        return toSessionView(saved);
    }

    @Override
    @Transactional
    public WorkoutSessionView cancelSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        validateTransitionAllowed(entity);
        entity.setStatus(WorkoutSessionStatus.CANCELLED);
        return toSessionView(workoutSessionRepository.save(entity));
    }

    @Override
    @Transactional
    public WorkoutSessionView appendOrReplaceExercise(CurrentUserPrincipal currentUser,
                                                      UUID sessionId,
                                                      WorkoutExerciseLogInput request) {
        validateExerciseLog(request);
        WorkoutSessionEntity session = findSession(currentUser.userId(), sessionId);
        validateLiveEditable(session);
        session.getExercises().removeIf(exercise -> exercise.getExercise().getExerciseId().equals(request.exerciseId()));
        session.getExercises().add(toSessionExerciseLog(currentUser.userId(), session, request, true));
        validateSessionExerciseSequences(session);
        deriveSessionMetrics(session);
        return toSessionView(workoutSessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionView upsertSet(CurrentUserPrincipal currentUser,
                                        UUID sessionId,
                                        UUID exerciseLogId,
                                        int setNumber,
                                        UpsertWorkoutExerciseSetRequest request) {
        if (setNumber < 1) {
            throw new IllegalArgumentException("setNumber must be at least 1");
        }
        WorkoutSessionEntity session = findSession(currentUser.userId(), sessionId);
        validateLiveEditable(session);
        WorkoutExerciseLogEntity exercise = findExerciseLog(session, exerciseLogId);
        WorkoutExerciseSetLogEntity setLog = exercise.getSets().stream()
                .filter(set -> set.getSetNumber().equals(setNumber))
                .findFirst()
                .orElseGet(() -> {
                    WorkoutExerciseSetLogEntity created = new WorkoutExerciseSetLogEntity();
                    created.setSetLogId(UUID.randomUUID());
                    created.setExerciseLog(exercise);
                    created.setSetNumber(setNumber);
                    exercise.getSets().add(created);
                    return created;
                });
        applySetLogRequest(setLog, request, true);
        deriveExerciseMetrics(exercise);
        deriveSessionMetrics(session);
        return toSessionView(workoutSessionRepository.save(session));
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

    private ImportWorkoutSessionRowResult importOneRow(UUID userId,
                                                       int rowIndex,
                                                       ImportWorkoutSessionRowRequest row) {
        try {
            return importRowTransactionTemplate.execute(status -> importSessionRow(userId, rowIndex, row));
        } catch (DataIntegrityViolationException ex) {
            return new ImportWorkoutSessionRowResult(
                    rowIndex,
                    row == null ? null : row.externalId(),
                    row == null ? null : parseImportSourceOrNull(row.source()),
                    ImportWorkoutSessionRowStatus.SKIPPED_DUPLICATE,
                    null,
                    "Duplicate externalId for source");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return new ImportWorkoutSessionRowResult(
                    rowIndex,
                    row == null ? null : row.externalId(),
                    row == null ? null : parseImportSourceOrNull(row.source()),
                    ImportWorkoutSessionRowStatus.REJECTED,
                    null,
                    ex.getMessage());
        }
    }

    private ImportWorkoutSessionRowResult importSessionRow(UUID userId,
                                                           int rowIndex,
                                                           ImportWorkoutSessionRowRequest row) {
        validateImportIdentity(row);
        WorkoutSource source = parseImportSource(row.source());
        if (workoutSessionRepository.findByUserIdAndSourceAndExternalId(userId, source, row.externalId()).isPresent()) {
            return new ImportWorkoutSessionRowResult(
                    rowIndex,
                    row.externalId(),
                    source,
                    ImportWorkoutSessionRowStatus.SKIPPED_DUPLICATE,
                    null,
                    "Duplicate externalId for source");
        }

        validateImportRow(row);
        UpsertWorkoutSessionRequest request = toUpsertWorkoutSessionRequest(row, source);
        validateSessionRequest(userId, request);

        WorkoutSessionEntity entity = new WorkoutSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setExternalId(row.externalId());
        applySessionRequest(userId, entity, request);
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        if (saved.getStatus() == WorkoutSessionStatus.COMPLETED) {
            publishWorkoutLoggedAndPersonalRecords(saved, true);
        }
        return new ImportWorkoutSessionRowResult(
                rowIndex,
                row.externalId(),
                source,
                ImportWorkoutSessionRowStatus.IMPORTED,
                saved.getSessionId(),
                null);
    }

    private void validateImportIdentity(ImportWorkoutSessionRowRequest row) {
        if (row == null) {
            throw new IllegalArgumentException("Import row must not be null");
        }
        if (row.externalId() == null || row.externalId().isBlank() || row.externalId().length() > 120) {
            throw new IllegalArgumentException("externalId is required and must not exceed 120 characters");
        }
        if (row.source() == null || row.source().isBlank() || row.source().length() > 30) {
            throw new IllegalArgumentException("source must be DEVICE or IMPORTED");
        }
    }

    private void validateImportRow(ImportWorkoutSessionRowRequest row) {
        if (row == null) {
            throw new IllegalArgumentException("Import row must not be null");
        }
        Set<ConstraintViolation<ImportWorkoutSessionRowRequest>> violations = validator.validate(row);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .findFirst()
                    .orElse("Import row is invalid");
            throw new IllegalArgumentException(message);
        }
    }

    private WorkoutSource parseImportSource(String source) {
        WorkoutSource parsed;
        try {
            parsed = WorkoutSource.valueOf(source.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("source must be DEVICE or IMPORTED");
        }
        if (parsed != WorkoutSource.DEVICE && parsed != WorkoutSource.IMPORTED) {
            throw new IllegalArgumentException("source must be DEVICE or IMPORTED");
        }
        return parsed;
    }

    private WorkoutSource parseImportSourceOrNull(String source) {
        if (source == null) {
            return null;
        }
        try {
            return WorkoutSource.valueOf(source.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private UpsertWorkoutSessionRequest toUpsertWorkoutSessionRequest(ImportWorkoutSessionRowRequest row,
                                                                      WorkoutSource source) {
        return new UpsertWorkoutSessionRequest(
                row.planId(),
                row.name(),
                row.workoutType(),
                row.status(),
                source,
                row.startedAt(),
                row.completedAt(),
                row.durationMinutes(),
                row.caloriesBurned(),
                row.perceivedExertion(),
                row.notes(),
                row.exercises());
    }

    private WorkoutPlanEntity toDuplicatePlan(UUID userId, WorkoutPlanEntity source) {
        WorkoutPlanEntity copy = new WorkoutPlanEntity();
        copy.setPlanId(UUID.randomUUID());
        copy.setUserId(userId);
        copy.setName(toCopyName(source.getName()));
        copy.setDescription(source.getDescription());
        copy.setGoalFocus(source.getGoalFocus());
        copy.setWorkoutType(source.getWorkoutType());
        copy.setDifficulty(source.getDifficulty());
        copy.setEstimatedDurationMinutes(source.getEstimatedDurationMinutes());
        copy.setStatus(WorkoutPlanStatus.DRAFT);
        for (WorkoutPlanExerciseEntity sourceExercise : source.getExercises()) {
            copy.getExercises().add(toDuplicatePlanExercise(copy, sourceExercise));
        }
        return copy;
    }

    private String toCopyName(String sourceName) {
        int maxBaseLength = PLAN_NAME_MAX_LENGTH - COPY_SUFFIX.length();
        String baseName = sourceName.length() > maxBaseLength ? sourceName.substring(0, maxBaseLength) : sourceName;
        return baseName + COPY_SUFFIX;
    }

    private WorkoutPlanExerciseEntity toDuplicatePlanExercise(WorkoutPlanEntity plan,
                                                              WorkoutPlanExerciseEntity source) {
        WorkoutPlanExerciseEntity copy = new WorkoutPlanExerciseEntity();
        copy.setPlanExerciseId(UUID.randomUUID());
        copy.setPlan(plan);
        copy.setExercise(source.getExercise());
        copy.setExerciseNameSnapshot(source.getExerciseNameSnapshot());
        copy.setSequence(source.getSequence());
        copy.setTargetSets(source.getTargetSets());
        copy.setTargetReps(source.getTargetReps());
        copy.setTargetWeightKg(source.getTargetWeightKg());
        copy.setTargetDurationSeconds(source.getTargetDurationSeconds());
        copy.setTargetDistanceKm(source.getTargetDistanceKm());
        copy.setRestSeconds(source.getRestSeconds());
        copy.setNotes(source.getNotes());
        for (WorkoutPlanExerciseSetTargetEntity sourceSet : source.getSetTargets()) {
            copy.getSetTargets().add(toDuplicateSetTarget(copy, sourceSet));
        }
        return copy;
    }

    private WorkoutPlanExerciseSetTargetEntity toDuplicateSetTarget(WorkoutPlanExerciseEntity exercise,
                                                                    WorkoutPlanExerciseSetTargetEntity source) {
        WorkoutPlanExerciseSetTargetEntity copy = new WorkoutPlanExerciseSetTargetEntity();
        copy.setSetTargetId(UUID.randomUUID());
        copy.setPlanExercise(exercise);
        copy.setSetNumber(source.getSetNumber());
        copy.setTargetReps(source.getTargetReps());
        copy.setTargetWeightKg(source.getTargetWeightKg());
        copy.setTargetDurationSeconds(source.getTargetDurationSeconds());
        copy.setTargetDistanceKm(source.getTargetDistanceKm());
        copy.setRestSeconds(source.getRestSeconds());
        return copy;
    }

    private WorkoutSessionEntity toRepeatedSession(UUID userId, WorkoutSessionEntity source) {
        WorkoutSessionEntity repeated = new WorkoutSessionEntity();
        repeated.setSessionId(UUID.randomUUID());
        repeated.setUserId(userId);
        repeated.setPlanId(source.getPlanId());
        repeated.setName(source.getName());
        repeated.setWorkoutType(source.getWorkoutType());
        repeated.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        repeated.setSource(WorkoutSource.MANUAL);
        repeated.setStartedAt(Instant.now());
        for (WorkoutExerciseLogEntity sourceExercise : source.getExercises()) {
            List<WorkoutExerciseSetLogEntity> loggedSets = sourceExercise.getSets().stream()
                    .filter(WorkoutExerciseSetLogEntity::isLogged)
                    .toList();
            if (!loggedSets.isEmpty()) {
                repeated.getExercises().add(toRepeatedExerciseLog(repeated, sourceExercise, loggedSets));
            }
        }
        return repeated;
    }

    private WorkoutExerciseLogEntity toRepeatedExerciseLog(WorkoutSessionEntity session,
                                                           WorkoutExerciseLogEntity source,
                                                           List<WorkoutExerciseSetLogEntity> loggedSets) {
        WorkoutExerciseLogEntity repeated = new WorkoutExerciseLogEntity();
        repeated.setExerciseLogId(UUID.randomUUID());
        repeated.setSession(session);
        repeated.setExercise(source.getExercise());
        repeated.setExerciseNameSnapshot(source.getExerciseNameSnapshot());
        repeated.setSequence(source.getSequence());
        repeated.setNotes(source.getNotes());
        for (WorkoutExerciseSetLogEntity sourceSet : loggedSets) {
            repeated.getSets().add(toRepeatedSetLog(repeated, sourceSet));
        }
        return repeated;
    }

    private WorkoutExerciseSetLogEntity toRepeatedSetLog(WorkoutExerciseLogEntity exercise,
                                                         WorkoutExerciseSetLogEntity source) {
        WorkoutExerciseSetLogEntity repeated = new WorkoutExerciseSetLogEntity();
        repeated.setSetLogId(UUID.randomUUID());
        repeated.setExerciseLog(exercise);
        repeated.setSetNumber(source.getSetNumber());
        repeated.setReps(source.getReps());
        repeated.setWeightKg(source.getWeightKg());
        repeated.setDurationSeconds(source.getDurationSeconds());
        repeated.setDistanceKm(source.getDistanceKm());
        repeated.setRestSeconds(source.getRestSeconds());
        repeated.setPerceivedExertion(source.getPerceivedExertion());
        repeated.setNotes(source.getNotes());
        repeated.setLogged(false);
        return repeated;
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
        deriveSessionMetrics(entity);
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
            session.getExercises().add(toSessionExerciseLog(userId, session, input, true));
        }
    }

    private void deriveSessionMetrics(WorkoutSessionEntity session) {
        if (session.getStartedAt() != null && session.getCompletedAt() != null) {
            long minutes = Duration.between(session.getStartedAt(), session.getCompletedAt()).toMinutes();
            session.setDurationMinutes(Math.toIntExact(minutes));
        }
        session.getExercises().forEach(this::deriveExerciseMetrics);
    }

    private void deriveExerciseMetrics(WorkoutExerciseLogEntity exercise) {
        List<WorkoutExerciseSetLogEntity> loggedSets = exercise.getSets().stream()
                .filter(WorkoutExerciseSetLogEntity::isLogged)
                .toList();
        if (loggedSets.isEmpty()) {
            return;
        }

        int performedReps = 0;
        BigDecimal totalVolumeKg = BigDecimal.ZERO;
        for (WorkoutExerciseSetLogEntity set : loggedSets) {
            if (set.getReps() != null) {
                performedReps += set.getReps();
                if (set.getWeightKg() != null) {
                    totalVolumeKg = totalVolumeKg.add(set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps())));
                }
            }
        }

        exercise.setPerformedSets(loggedSets.size());
        exercise.setPerformedReps(performedReps);
        exercise.setTotalVolumeKg(totalVolumeKg);
    }

    private WorkoutExerciseLogEntity toSessionExerciseLog(UUID userId,
                                                          WorkoutSessionEntity session,
                                                          WorkoutExerciseLogInput input,
                                                          boolean logged) {
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
        replaceSetLogs(exercise, input.sets(), logged);
        deriveExerciseMetrics(exercise);
        return exercise;
    }

    private void replaceSetLogs(WorkoutExerciseLogEntity exercise,
                                List<WorkoutExerciseSetLogInput> inputs,
                                boolean logged) {
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
            applySetLogInput(setLog, input, logged);
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
        setLog.setLogged(false);
        return setLog;
    }

    private WorkoutExerciseLogEntity findExerciseLog(WorkoutSessionEntity session, UUID exerciseLogId) {
        return session.getExercises().stream()
                .filter(exercise -> exercise.getExerciseLogId().equals(exerciseLogId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Workout exercise log not found"));
    }

    private void applySetLogInput(WorkoutExerciseSetLogEntity setLog,
                                  WorkoutExerciseSetLogInput input,
                                  boolean logged) {
        setLog.setReps(input.reps());
        setLog.setWeightKg(input.weightKg());
        setLog.setDurationSeconds(input.durationSeconds());
        setLog.setDistanceKm(input.distanceKm());
        setLog.setRestSeconds(input.restSeconds());
        setLog.setPerceivedExertion(input.perceivedExertion());
        setLog.setNotes(input.notes());
        setLog.setLogged(logged);
    }

    private void applySetLogRequest(WorkoutExerciseSetLogEntity setLog,
                                    UpsertWorkoutExerciseSetRequest request,
                                    boolean logged) {
        setLog.setReps(request.reps());
        setLog.setWeightKg(request.weightKg());
        setLog.setDurationSeconds(request.durationSeconds());
        setLog.setDistanceKm(request.distanceKm());
        setLog.setRestSeconds(request.restSeconds());
        setLog.setPerceivedExertion(request.perceivedExertion());
        setLog.setNotes(request.notes());
        setLog.setLogged(logged);
    }

    private ExerciseEntity findAccessibleExercise(UUID userId, UUID exerciseId) {
        return exerciseRepository.findAccessibleById(exerciseId, userId, ExerciseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
    }

    /*
     * WO-6 transition matrix:
     * PLANNED / IN_PROGRESS can be live-edited, completed, or cancelled.
     * COMPLETED / CANCELLED are terminal for live edit and lifecycle transition endpoints.
     */
    private void validateLiveEditable(WorkoutSessionEntity session) {
        if (session.getStatus() != WorkoutSessionStatus.PLANNED
                && session.getStatus() != WorkoutSessionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Only planned or in-progress workout sessions can be edited live");
        }
    }

    private void validateTransitionAllowed(WorkoutSessionEntity session) {
        if (session.getStatus() != WorkoutSessionStatus.PLANNED
                && session.getStatus() != WorkoutSessionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Only planned or in-progress workout sessions can transition");
        }
    }

    private void validateSessionRequest(UUID userId, UpsertWorkoutSessionRequest request) {
        validateSessionTimingAndScalars(
                request.startedAt(),
                request.completedAt(),
                request.status(),
                request.durationMinutes(),
                request.caloriesBurned());
        validateExerciseLogs(request.exercises());
        if (request.planId() != null) {
            WorkoutPlanEntity plan = findPlan(userId, request.planId());
            if (plan.getStatus() == WorkoutPlanStatus.ARCHIVED) {
                throw new IllegalArgumentException("Archived workout plan cannot be used for a session");
            }
        }
    }

    private void validateSessionTimingAndScalars(Instant startedAt,
                                                 Instant completedAt,
                                                 WorkoutSessionStatus status,
                                                 Integer durationMinutes,
                                                 Integer caloriesBurned) {
        validateTimeRange(startedAt, completedAt);
        if (startedAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("startedAt must not be in the future");
        }
        if (status == WorkoutSessionStatus.COMPLETED && completedAt == null) {
            throw new IllegalArgumentException("completedAt is required when status is COMPLETED");
        }
        if (status != WorkoutSessionStatus.COMPLETED && completedAt != null) {
            throw new IllegalArgumentException("completedAt is only allowed when status is COMPLETED");
        }
        if (durationMinutes != null && durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must be non-negative");
        }
        if (caloriesBurned != null && caloriesBurned < 0) {
            throw new IllegalArgumentException("caloriesBurned must be non-negative");
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

    private void validateExerciseLog(WorkoutExerciseLogInput exercise) {
        validateUniqueNumbers(
                exercise.sets().stream()
                        .map(WorkoutExerciseSetLogInput::setNumber)
                        .toList(),
                "Set numbers must be unique within an exercise log");
    }

    private void validateSessionExerciseSequences(WorkoutSessionEntity session) {
        validateUniqueNumbers(
                session.getExercises().stream()
                        .map(WorkoutExerciseLogEntity::getSequence)
                        .toList(),
                "Exercise sequence values must be unique within the session");
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

    private void validateHistoryRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(exerciseHistoryMaxRangeDays)) > 0) {
            throw new IllegalArgumentException("Exercise history range must not exceed "
                    + exerciseHistoryMaxRangeDays + " days");
        }
    }

    private ExerciseRecordsView toExerciseRecordsView(ExerciseEntity exercise, PersonalRecordSnapshot snapshot) {
        return new ExerciseRecordsView(
                exercise.getExerciseId(),
                exercise.getName(),
                snapshot.maxWeightKg(),
                snapshot.maxReps(),
                snapshot.maxSingleSetVolumeKg(),
                snapshot.estimatedOneRepMaxKg(),
                snapshot.bestSessionVolumeKg());
    }

    private void publishWorkoutLoggedAndPersonalRecords(WorkoutSessionEntity session) {
        publishWorkoutLoggedAndPersonalRecords(session, false);
    }

    private void publishWorkoutLoggedAndPersonalRecords(WorkoutSessionEntity session, boolean skipPersonalRecords) {
        publishWorkoutLogged(session);
        if (!skipPersonalRecords) {
            publishPersonalRecordsAchieved(session);
        }
    }

    private void publishPersonalRecordsAchieved(WorkoutSessionEntity session) {
        for (WorkoutExerciseLogEntity exercise : session.getExercises()) {
            PersonalRecordSnapshot current = snapshotExercise(exercise);
            if (current.isEmpty()) {
                continue;
            }
            PersonalRecordSnapshot prior = snapshotRows(workoutExerciseLogRepository.findRecordRows(
                    session.getUserId(),
                    exercise.getExercise().getExerciseId(),
                    session.getSessionId()));
            List<PersonalRecordAchievedPayload.RecordAchievement> achievements = compareRecords(current, prior);
            if (achievements.isEmpty()) {
                continue;
            }
            domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", session.getSessionId().toString(), new FitSyncDomainEvent<>(
                    UUID.randomUUID(),
                    "PersonalRecordAchieved",
                    1,
                    Instant.now(),
                    SOURCE,
                    session.getUserId(),
                    new PersonalRecordAchievedPayload(
                            exercise.getExercise().getExerciseId(),
                            exercise.getExerciseNameSnapshot(),
                            session.getSessionId(),
                            achievements,
                            session.getCompletedAt())));
        }
    }

    private List<PersonalRecordAchievedPayload.RecordAchievement> compareRecords(PersonalRecordSnapshot current,
                                                                                PersonalRecordSnapshot prior) {
        List<PersonalRecordAchievedPayload.RecordAchievement> achievements = new ArrayList<>();
        addAchievementIfNew(achievements, MAX_WEIGHT_KG, current.maxWeightKg(), prior.maxWeightKg());
        addAchievementIfNew(achievements, MAX_REPS, current.maxReps(), prior.maxReps());
        addAchievementIfNew(achievements, MAX_SINGLE_SET_VOLUME_KG, current.maxSingleSetVolumeKg(), prior.maxSingleSetVolumeKg());
        addAchievementIfNew(achievements, ESTIMATED_ONE_REP_MAX_KG, current.estimatedOneRepMaxKg(), prior.estimatedOneRepMaxKg());
        addAchievementIfNew(achievements, BEST_SESSION_VOLUME_KG, current.bestSessionVolumeKg(), prior.bestSessionVolumeKg());
        return achievements;
    }

    private void addAchievementIfNew(List<PersonalRecordAchievedPayload.RecordAchievement> achievements,
                                     String recordType,
                                     ExerciseRecordValueView current,
                                     ExerciseRecordValueView prior) {
        if (current != null && (prior == null || current.value().compareTo(prior.value()) > 0)) {
            achievements.add(new PersonalRecordAchievedPayload.RecordAchievement(recordType, current.value()));
        }
    }

    private PersonalRecordSnapshot snapshotExercise(WorkoutExerciseLogEntity exercise) {
        List<ExerciseRecordSetRow> rows = exercise.getSets().stream()
                .filter(WorkoutExerciseSetLogEntity::isLogged)
                .map(set -> new ExerciseRecordSetRow(
                        exercise.getSession().getSessionId(),
                        exercise.getExerciseLogId(),
                        exercise.getExercise().getExerciseId(),
                        exercise.getExerciseNameSnapshot(),
                        exercise.getSession().getStartedAt(),
                        exercise.getSession().getCompletedAt(),
                        set.getReps(),
                        set.getWeightKg()))
                .toList();
        return snapshotRows(rows);
    }

    private PersonalRecordSnapshot snapshotRows(List<ExerciseRecordSetRow> rows) {
        ExerciseRecordValueView maxWeight = null;
        ExerciseRecordValueView maxReps = null;
        ExerciseRecordValueView maxSingleSetVolume = null;
        ExerciseRecordValueView estimatedOneRepMax = null;
        Map<UUID, SessionVolume> sessionVolumes = new LinkedHashMap<>();

        for (ExerciseRecordSetRow row : rows) {
            if (row.weightKg() != null) {
                maxWeight = max(maxWeight, recordValue(row.weightKg(), row));
            }
            if (row.reps() != null) {
                maxReps = max(maxReps, recordValue(BigDecimal.valueOf(row.reps()), row));
            }
            if (row.reps() != null && row.weightKg() != null) {
                BigDecimal setVolume = row.weightKg().multiply(BigDecimal.valueOf(row.reps()));
                maxSingleSetVolume = max(maxSingleSetVolume, recordValue(setVolume, row));
                estimatedOneRepMax = max(estimatedOneRepMax, recordValue(estimateOneRepMax(row.weightKg(), row.reps()), row));
                sessionVolumes.compute(row.sessionId(), (sessionId, volume) -> {
                    if (volume == null) {
                        return new SessionVolume(row.sessionId(), row.completedAt(), setVolume);
                    }
                    return new SessionVolume(volume.sessionId(), volume.achievedAt(), volume.value().add(setVolume));
                });
            }
        }

        ExerciseRecordValueView bestSessionVolume = null;
        for (SessionVolume volume : sessionVolumes.values()) {
            bestSessionVolume = max(bestSessionVolume, new ExerciseRecordValueView(
                    normalize(volume.value()),
                    volume.sessionId(),
                    volume.achievedAt()));
        }

        return new PersonalRecordSnapshot(
                maxWeight,
                maxReps,
                maxSingleSetVolume,
                estimatedOneRepMax,
                bestSessionVolume);
    }

    private ExerciseRecordValueView max(ExerciseRecordValueView current, ExerciseRecordValueView candidate) {
        return current == null || candidate.value().compareTo(current.value()) > 0 ? candidate : current;
    }

    private ExerciseRecordValueView recordValue(BigDecimal value, ExerciseRecordSetRow row) {
        return new ExerciseRecordValueView(normalize(value), row.sessionId(), row.completedAt());
    }

    /*
     * Epley estimated one-rep max: weight * (1 + reps / 30).
     */
    private BigDecimal estimateOneRepMax(BigDecimal weight, int reps) {
        BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(reps)
                .divide(BigDecimal.valueOf(30), 8, RoundingMode.HALF_UP));
        return weight.multiply(multiplier);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
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
                                        setLog.isLogged(),
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

    private record PersonalRecordSnapshot(
            ExerciseRecordValueView maxWeightKg,
            ExerciseRecordValueView maxReps,
            ExerciseRecordValueView maxSingleSetVolumeKg,
            ExerciseRecordValueView estimatedOneRepMaxKg,
            ExerciseRecordValueView bestSessionVolumeKg) {

        boolean isEmpty() {
            return maxWeightKg == null
                    && maxReps == null
                    && maxSingleSetVolumeKg == null
                    && estimatedOneRepMaxKg == null
                    && bestSessionVolumeKg == null;
        }
    }

    private record SessionVolume(
            UUID sessionId,
            Instant achievedAt,
            BigDecimal value) {
    }
}
