package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import com.fitsync.workout.config.AutoCloseProperties;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.ActiveWorkoutSessionView;
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
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanDayView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetView;
import com.fitsync.workout.dto.WorkoutPlanExerciseView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutResumeSetView;
import com.fitsync.workout.dto.WorkoutSessionResumeView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.dto.SetRestRequest;
import com.fitsync.workout.dto.SessionEffortRequest;
import com.fitsync.workout.dto.NewRecordView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutPlanDayEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.entity.PlanEntry;
import com.fitsync.workout.entity.WorkoutPlanExerciseSetTargetEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.messaging.DomainEventPublisher;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.SessionActivityRow;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ConflictException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(WorkoutServiceImpl.class);

    private static final String SOURCE = "workout-service";
    private static final String ACTIVE_SESSION_EXISTS_CODE = "ACTIVE_SESSION_EXISTS";
    private static final String ACTIVE_SESSION_EXISTS_MESSAGE = "You have a workout in progress.";
    private static final String NOTHING_LOGGED_CODE = "NOTHING_LOGGED";
    private static final String NOTHING_LOGGED_MESSAGE = "No set was logged in this workout.";
    private static final String IMPORT_IN_PROGRESS_MESSAGE =
            "An imported workout is history and cannot be in progress.";
    /** How many in-progress sessions one user's check looks at; duplicates beyond it wait for the next check. */
    private static final int OPEN_SESSION_PAGE_SIZE = 50;
    private static final int SWEEP_PAGE_SIZE = 100;
    /** Bounds one sweep run, so a very large backlog is worked through over several runs. */
    private static final int SWEEP_MAX_PAGES = 50;
    private static final Duration AUTO_CLOSE_MINIMUM_DURATION = Duration.ofMinutes(1);
    private static final String MAX_WEIGHT_KG = "MAX_WEIGHT_KG";
    private static final String MAX_REPS = "MAX_REPS";
    private static final String MAX_SINGLE_SET_VOLUME_KG = "MAX_SINGLE_SET_VOLUME_KG";
    private static final String ESTIMATED_ONE_REP_MAX_KG = "ESTIMATED_ONE_REP_MAX_KG";
    private static final String BEST_SESSION_VOLUME_KG = "BEST_SESSION_VOLUME_KG";
    private static final String COPY_SUFFIX = " (Copy)";
    private static final int PLAN_NAME_MAX_LENGTH = 120;

    private static final String DUPLICATE_DAY_MESSAGE = "A plan can have one entry for each day of the week.";
    private static final String SPORT_DAY_NEEDS_ACTIVITY_MESSAGE = "A sport day needs a sport activity.";
    private static final String SPORT_DAY_HAS_NO_EXERCISES_MESSAGE = "A sport day has no exercises.";
    private static final String WEEK_EXCLUSIVE_EXERCISES_MESSAGE = "A plan with a week lists its exercises under its days.";
    private static final String FLAT_PLAN_HAS_NO_WEEK_MESSAGE = "This plan has no week.";
    private static final String PLAN_HAS_NOTHING_ON_THIS_DAY_MESSAGE = "The plan has nothing on this day.";

    private final String workoutEventsTopic;
    private final int exerciseHistoryMaxRangeDays;
    private final Clock clock;
    private final DomainEventPublisher domainEventPublisher;
    private final ExerciseService exerciseService;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final Validator validator;
    private final SessionKindResolver sessionKindResolver;
    private final TransactionTemplate importRowTransactionTemplate;
    private final TransactionTemplate sweepTransactionTemplate;
    private final AutoCloseProperties autoCloseProperties;

    public WorkoutServiceImpl(@Value("${fitsync.kafka.topics.workout-events:workout-events}") String workoutEventsTopic,
                              @Value("${fitsync.workout.exercise-history.max-range-days:370}") int exerciseHistoryMaxRangeDays,
                              Clock clock,
                              DomainEventPublisher domainEventPublisher,
                              ExerciseService exerciseService,
                              ExerciseRepository exerciseRepository,
                              WorkoutExerciseLogRepository workoutExerciseLogRepository,
                              WorkoutPlanRepository workoutPlanRepository,
                              WorkoutSessionRepository workoutSessionRepository,
                              Validator validator,
                              SessionKindResolver sessionKindResolver,
                              PlatformTransactionManager transactionManager,
                              AutoCloseProperties autoCloseProperties) {
        this.workoutEventsTopic = workoutEventsTopic;
        this.exerciseHistoryMaxRangeDays = exerciseHistoryMaxRangeDays;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
        this.exerciseService = exerciseService;
        this.exerciseRepository = exerciseRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.validator = validator;
        this.sessionKindResolver = sessionKindResolver;
        this.importRowTransactionTemplate = new TransactionTemplate(transactionManager);
        this.importRowTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.sweepTransactionTemplate = new TransactionTemplate(transactionManager);
        this.sweepTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.autoCloseProperties = autoCloseProperties;
    }

    @Override
    @Transactional
    public WorkoutPlanView createPlan(CurrentUserPrincipal currentUser, CreateWorkoutPlanRequest request) {
        validatePlanExerciseList(request.exercises());
        validatePlanWeek(request.exercises(), request.days());
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
        replacePlanExercisesAndDays(currentUser.userId(), entity, request.exercises(), request.days());
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
                Instant.now(clock),
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
        validatePlanExerciseList(request.exercises());
        validatePlanWeek(request.exercises(), request.days());
        WorkoutPlanEntity entity = findPlan(currentUser.userId(), planId);
        if (entity.getStatus() == WorkoutPlanStatus.ARCHIVED) {
            throw new IllegalArgumentException("An archived workout plan cannot be edited");
        }
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setGoalFocus(request.goalFocus());
        entity.setWorkoutType(request.workoutType());
        entity.setDifficulty(request.difficulty());
        entity.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        replacePlanExercisesAndDays(currentUser.userId(), entity, request.exercises(), request.days());
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
        return startPlan(currentUser, planId, null, "UTC");
    }

    @Override
    @Transactional
    public WorkoutSessionView startPlan(CurrentUserPrincipal currentUser, UUID planId, UUID planDayId, String timezone) {
        WorkoutPlanEntity plan = findPlan(currentUser.userId(), planId);
        if (plan.getStatus() == WorkoutPlanStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archived workout plan cannot be used for a session");
        }
        requireNoActiveSession(currentUser.userId(), null);

        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(currentUser.userId());
        session.setPlanId(plan.getPlanId());
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        session.setSource(WorkoutSource.PLAN);
        session.setStartedAt(Instant.now(clock));

        if (plan.getDays().isEmpty()) {
            if (planDayId != null) {
                throw new IllegalArgumentException(FLAT_PLAN_HAS_NO_WEEK_MESSAGE);
            }
            session.setName(plan.getName());
            session.setWorkoutType(plan.getWorkoutType());
            for (PlanEntry planExercise : plan.getEntries()) {
                session.getEntries().add(toSeededExerciseLog(session, planExercise));
            }
        } else {
            WorkoutPlanDayEntity day = resolvePlanDay(plan, planDayId, timezone);
            session.setPlanDayId(day.getPlanDayId());
            session.setName(day.getTitle());
            session.setWorkoutType(toWorkoutType(day.getKind()));
            session.setSportActivity(day.getSportActivity());
            for (PlanEntry planExercise : plan.getEntries()) {
                if (day.getPlanDayId().equals(planDayIdOf(planExercise))) {
                    session.getEntries().add(toSeededExerciseLog(session, planExercise));
                }
            }
        }
        applySessionKind(session);

        return toSessionView(workoutSessionRepository.save(session));
    }

    /**
     * Resolves which plan day a start request seeds a session from. An explicit planDayId must
     * belong to this plan, which the plan lookup already scoped to the caller. With no planDayId
     * the day is today's day of week in the given timezone (Monday = 1).
     */
    private WorkoutPlanDayEntity resolvePlanDay(WorkoutPlanEntity plan, UUID planDayId, String timezone) {
        if (planDayId != null) {
            return plan.getDays().stream()
                    .filter(day -> day.getPlanDayId().equals(planDayId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Workout plan day not found"));
        }
        ZoneId zoneId = InsightDates.resolveZone(timezone);
        int todayDayOfWeek = LocalDate.now(clock.withZone(zoneId)).getDayOfWeek().getValue();
        return plan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == todayDayOfWeek)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(PLAN_HAS_NOTHING_ON_THIS_DAY_MESSAGE));
    }

    private UUID planDayIdOf(PlanEntry planExercise) {
        return planExercise.getPlanDay() == null ? null : planExercise.getPlanDay().getPlanDayId();
    }

    private WorkoutType toWorkoutType(SessionKind kind) {
        return switch (kind) {
            case STRENGTH -> WorkoutType.STRENGTH;
            case CARDIO -> WorkoutType.CARDIO;
            case MOBILITY -> WorkoutType.MOBILITY;
            case SPORT -> WorkoutType.SPORTS;
        };
    }

    @Override
    @Transactional
    public WorkoutSessionView createSession(CurrentUserPrincipal currentUser, UpsertWorkoutSessionRequest request) {
        validateSessionRequest(currentUser.userId(), request);
        if (request.status() == WorkoutSessionStatus.IN_PROGRESS) {
            requireNoActiveSession(currentUser.userId(), null);
        }
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
                                                         WorkoutType workoutType,
                                                         Integer minDurationMinutes,
                                                         Integer maxDurationMinutes,
                                                         UUID planId,
                                                         String q,
                                                         int page,
                                                         int size) {
        validateSessionFilterRange(from, to);
        validateDurationRange(minDurationMinutes, maxDurationMinutes);
        UUID userId = currentUser.userId();
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "sessionId")));
        Page<UUID> sessionIds = workoutSessionRepository.findHistoryIds(
                        userId,
                        from,
                        to,
                        status,
                        workoutType,
                        minDurationMinutes,
                        maxDurationMinutes,
                        planId,
                        q,
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

    private void validateDurationRange(Integer minDurationMinutes, Integer maxDurationMinutes) {
        if (minDurationMinutes != null && maxDurationMinutes != null && minDurationMinutes > maxDurationMinutes) {
            throw new IllegalArgumentException("minDurationMinutes must not be greater than maxDurationMinutes");
        }
    }

    private void validateSessionFilterRange(Instant from, Instant to) {
        if (isAfterWhenBothPresent(from, to)) {
            throw new IllegalArgumentException("to must not be before from");
        }
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
        return workoutSessionRepository
                .findTopByUserIdAndStatusOrderByCompletedAtDesc(currentUser.userId(), WorkoutSessionStatus.COMPLETED)
                .map(this::toSessionView)
                .orElseThrow(() -> new ResourceNotFoundException("Completed workout session not found"));
    }

    @Override
    @Transactional
    public Optional<ActiveWorkoutSessionView> getActiveSession(CurrentUserPrincipal currentUser) {
        UUID userId = currentUser.userId();
        return settleOpenSessions(userId)
                .map(open -> findSession(userId, open.getSessionId()))
                .map(session -> new ActiveWorkoutSessionView(toSessionView(session), toResumeView(session)));
    }

    @Override
    public StaleSessionSweepResult closeStaleSessions() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(autoCloseProperties.idle());
        int completed = 0;
        int cancelled = 0;
        int failed = 0;
        Instant afterStartedAt = Instant.EPOCH;
        UUID afterSessionId = new UUID(0L, 0L);
        for (int page = 0; page < SWEEP_MAX_PAGES; page++) {
            List<WorkoutSessionEntity> batch = workoutSessionRepository.findSweepCandidates(
                    WorkoutSessionStatus.IN_PROGRESS,
                    cutoff,
                    afterStartedAt,
                    afterSessionId,
                    PageRequest.of(0, SWEEP_PAGE_SIZE));
            for (WorkoutSessionEntity candidate : batch) {
                UUID candidateId = candidate.getSessionId();
                UUID candidateUserId = candidate.getUserId();
                try {
                    AutoCloseOutcome outcome = sweepTransactionTemplate.execute(
                            status -> closeIfStale(candidateId, candidateUserId, now, cutoff));
                    if (outcome == AutoCloseOutcome.COMPLETED) {
                        completed++;
                    } else if (outcome == AutoCloseOutcome.CANCELLED) {
                        cancelled++;
                    }
                } catch (RuntimeException ex) {
                    // The next run tries again. Only the kind of failure is logged, never the session or the user.
                    failed++;
                    LOG.warn("event=stale_session_close_failed errorType={}", ex.getClass().getSimpleName());
                }
            }
            if (batch.size() < SWEEP_PAGE_SIZE) {
                break;
            }
            WorkoutSessionEntity last = batch.get(batch.size() - 1);
            afterStartedAt = last.getStartedAt();
            afterSessionId = last.getSessionId();
        }
        StaleSessionSweepResult result = new StaleSessionSweepResult(completed, cancelled, failed);
        if (result.closed() > 0 || failed > 0) {
            LOG.info("event=stale_sessions_swept completed={} cancelled={} failed={}", completed, cancelled, failed);
        }
        return result;
    }

    /**
     * Closes one session of the sweep if it is stale when looked at again inside its own transaction.
     * The session may have been finished, or logged to, since the sweep read it.
     */
    private AutoCloseOutcome closeIfStale(UUID sessionId, UUID userId, Instant now, Instant cutoff) {
        WorkoutSessionEntity session = workoutSessionRepository.findById(sessionId).orElse(null);
        if (session == null
                || !userId.equals(session.getUserId())
                || session.getStatus() != WorkoutSessionStatus.IN_PROGRESS) {
            return AutoCloseOutcome.KEPT;
        }
        List<WorkoutSessionEntity> newest = workoutSessionRepository.findOpenSessions(
                userId, WorkoutSessionStatus.IN_PROGRESS, PageRequest.of(0, 1));
        boolean isNewest = !newest.isEmpty() && newest.get(0).getSessionId().equals(sessionId);
        SessionActivityRow activity = sessionActivity(List.of(sessionId)).get(sessionId);
        if (isNewest && !lastActivity(session, activity).isBefore(cutoff)) {
            return AutoCloseOutcome.KEPT;
        }
        return autoClose(session, activity, now);
    }

    /**
     * Closes the user's stale in-progress sessions and returns the one that stays open, if any. Only
     * the newest session may stay, and only while it has been worked on within the idle time; every
     * other in-progress session is closed, which also cleans up duplicates from before the rule.
     * Runs in the caller's transaction, so the answer and whatever the caller does next agree.
     */
    private Optional<WorkoutSessionEntity> settleOpenSessions(UUID userId) {
        List<WorkoutSessionEntity> open = workoutSessionRepository.findOpenSessions(
                userId, WorkoutSessionStatus.IN_PROGRESS, PageRequest.of(0, OPEN_SESSION_PAGE_SIZE));
        if (open.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(autoCloseProperties.idle());
        Map<UUID, SessionActivityRow> activity = sessionActivity(
                open.stream().map(WorkoutSessionEntity::getSessionId).toList());
        WorkoutSessionEntity active = null;
        for (int index = 0; index < open.size(); index++) {
            WorkoutSessionEntity session = open.get(index);
            SessionActivityRow row = activity.get(session.getSessionId());
            if (index == 0 && !lastActivity(session, row).isBefore(cutoff)) {
                active = session;
            } else {
                autoClose(session, row, now);
            }
        }
        return Optional.ofNullable(active);
    }

    private Map<UUID, SessionActivityRow> sessionActivity(List<UUID> sessionIds) {
        return workoutExerciseLogRepository
                .findSessionActivity(sessionIds, List.of(WorkoutSetStatus.DONE, WorkoutSetStatus.SKIPPED), WorkoutSetStatus.DONE)
                .stream()
                .collect(Collectors.toMap(SessionActivityRow::sessionId, row -> row));
    }

    /** The later of the start and the last time a set was marked done or skipped. */
    private Instant lastActivity(WorkoutSessionEntity session, SessionActivityRow activity) {
        Instant lastSetAt = activity == null ? null : activity.lastSetAt();
        return lastSetAt == null || lastSetAt.isBefore(session.getStartedAt()) ? session.getStartedAt() : lastSetAt;
    }

    /**
     * Closes a left workout. One with a set that was done is completed at the time of its last
     * activity through the same path as a manual completion, so duration, kind, totals and events
     * come out as they would have. One without is cancelled, since nothing was trained, and
     * publishes nothing.
     */
    private AutoCloseOutcome autoClose(WorkoutSessionEntity session, SessionActivityRow activity, Instant now) {
        if (activity != null && activity.hasDoneSet()) {
            Instant lastActivity = lastActivity(session, activity);
            Instant completedAt = lastActivity.isAfter(session.getStartedAt())
                    ? lastActivity
                    : session.getStartedAt().plus(AUTO_CLOSE_MINIMUM_DURATION);
            WorkoutSessionEntity full = findSession(session.getUserId(), session.getSessionId());
            full.setAutoClosedAt(now);
            completeEntity(full, completedAt);
            return AutoCloseOutcome.COMPLETED;
        }
        endCancelled(session, lastActivity(session, activity));
        session.setAutoClosedAt(now);
        workoutSessionRepository.save(session);
        return AutoCloseOutcome.CANCELLED;
    }

    /**
     * Refuses to put a second session in progress. Left workouts are closed first, so one that was
     * forgotten never blocks a new start. The check and the insert that follows share the caller's
     * transaction; without a database constraint two simultaneous requests can still both pass.
     */
    private void requireNoActiveSession(UUID userId, UUID exceptSessionId) {
        settleOpenSessions(userId)
                .filter(active -> !active.getSessionId().equals(exceptSessionId))
                .ifPresent(active -> {
                    throw new ConflictException(
                            ACTIVE_SESSION_EXISTS_CODE,
                            ACTIVE_SESSION_EXISTS_MESSAGE,
                            Map.of("sessionId", active.getSessionId().toString(),
                                    "name", active.getName(),
                                    "startedAt", active.getStartedAt().toString()));
                });
    }

    private enum AutoCloseOutcome { COMPLETED, CANCELLED, KEPT }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSessionView getSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        return toSessionView(findSession(currentUser.userId(), sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSessionResumeView resumeSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity session = findSession(currentUser.userId(), sessionId);
        return toResumeView(session);
    }

    @Override
    @Transactional
    public WorkoutSessionView repeatSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity source = findSession(currentUser.userId(), sessionId);
        requireNoActiveSession(currentUser.userId(), null);
        if (source.getStatus() != WorkoutSessionStatus.COMPLETED) {
            // Only the sets that were done are repeated, so anything else would start an empty workout.
            throw new IllegalArgumentException("Only a completed workout session can be repeated");
        }
        WorkoutSessionEntity repeated = toRepeatedSession(currentUser.userId(), source);
        return toSessionView(workoutSessionRepository.save(repeated));
    }

    @Override
    @Transactional
    public WorkoutSessionView repeatLatestSession(CurrentUserPrincipal currentUser) {
        WorkoutSessionEntity latest = workoutSessionRepository
                .findTopByUserIdAndStatusOrderByCompletedAtDesc(currentUser.userId(), WorkoutSessionStatus.COMPLETED)
                .orElseThrow(() -> new ResourceNotFoundException("Completed workout session not found"));
        WorkoutSessionEntity source = findSession(currentUser.userId(), latest.getSessionId());
        requireNoActiveSession(currentUser.userId(), null);
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
        if (request.status() == WorkoutSessionStatus.IN_PROGRESS && previousStatus != WorkoutSessionStatus.IN_PROGRESS) {
            // Reopening a session makes it the one in progress, so no other may be.
            requireNoActiveSession(currentUser.userId(), sessionId);
        }
        applySessionRequest(currentUser.userId(), entity, request);
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        if (previousStatus == WorkoutSessionStatus.COMPLETED && saved.getStatus() != WorkoutSessionStatus.COMPLETED) {
            // Reopened or cancelled by an edit: the totals drop it, as they do for a delete, and take
            // it back with a WorkoutLogged when it is completed again.
            publishWorkoutDeleted(saved);
        } else if (saved.getStatus() == WorkoutSessionStatus.COMPLETED && previousStatus != WorkoutSessionStatus.COMPLETED) {
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
        requireLoggedSetWhenGuided(entity);
        Completion completion = completeEntity(entity, request == null ? null : request.completedAt());
        return toSessionView(completion.session()).withNewRecords(completion.newRecords());
    }

    /** A session and the personal records it set, from the one path that completes sessions. */
    private record Completion(WorkoutSessionEntity session, List<NewRecordView> newRecords) {
    }

    /**
     * A guided workout, one with exercise logs, cannot be completed without a single set that is done
     * and logged. A session without exercise logs, such as a timed workout or a sport, is not asked
     * for sets. Only the person's own completion goes through this check; closing a left workout
     * completes only sessions that have a done set and cancels the others.
     */
    private void requireLoggedSetWhenGuided(WorkoutSessionEntity entity) {
        List<SessionEntry> exercises = entity.getSetEntries();
        if (!exercises.isEmpty() && exercises.stream().noneMatch(
                exercise -> exercise.getSets().stream().anyMatch(this::isDoneSet))) {
            throw new BadRequestException(NOTHING_LOGGED_CODE, NOTHING_LOGGED_MESSAGE);
        }
    }

    /**
     * The one way a session is completed, whether the person asks for it or the server closes a left
     * workout. Without a time the session ends now, but never before it started: the stored start
     * can be rounded up by the database, so a workout finished in the same instant it began would
     * otherwise fail its own timeline check.
     */
    private Completion completeEntity(WorkoutSessionEntity entity, Instant completedAt) {
        Instant endedAt = completedAt;
        if (endedAt == null) {
            Instant now = Instant.now(clock);
            endedAt = now.isBefore(entity.getStartedAt()) ? entity.getStartedAt() : now;
        }
        entity.setStatus(WorkoutSessionStatus.COMPLETED);
        entity.setCompletedAt(endedAt);
        validateSessionTimingAndScalars(
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getDurationMinutes(),
                entity.getCaloriesBurned());
        applySessionKind(entity);
        deriveSessionMetrics(entity);
        entity.setEndedAt(endedAt);
        List<ExerciseRecords> records = findNewRecords(entity);
        fillCaloriesEstimate(entity);
        fillEffortEstimate(entity, !records.isEmpty());
        WorkoutSessionEntity saved = workoutSessionRepository.save(entity);
        publishWorkoutLogged(saved);
        publishPersonalRecords(saved, records);
        return new Completion(saved, records.stream()
                .flatMap(exercise -> exercise.achievements().stream()
                        .map(record -> new NewRecordView(exercise.exerciseName(), record.recordType(), record.value())))
                .toList());
    }

    /**
     * Ends a session that was cancelled: it keeps when it ended and how long it ran, and an estimate
     * of its calories, so the session page can show them. It publishes nothing and counts nowhere.
     */
    private void endCancelled(WorkoutSessionEntity entity, Instant endedAt) {
        Instant end = endedAt.isBefore(entity.getStartedAt()) ? entity.getStartedAt() : endedAt;
        entity.setStatus(WorkoutSessionStatus.CANCELLED);
        entity.setEndedAt(end);
        entity.setDurationMinutes(Math.toIntExact(Duration.between(entity.getStartedAt(), end).toMinutes()));
        fillCaloriesEstimate(entity);
    }

    /** Estimates the calories from the kind and the minutes when the person gave none. */
    private void fillCaloriesEstimate(WorkoutSessionEntity entity) {
        if (entity.getCaloriesBurned() != null && !entity.isCaloriesEstimated()) {
            return;
        }
        Integer estimate = EffortEstimator.estimateCalories(entity.getWorkoutType(), entity.getDurationMinutes());
        entity.setCaloriesBurned(estimate);
        entity.setCaloriesEstimated(estimate != null);
    }

    /** Estimates the effort when the person gave none, against their last finished sessions of the same kind. */
    private void fillEffortEstimate(WorkoutSessionEntity entity, boolean newBest) {
        if (entity.getPerceivedExertion() != null && !entity.isEffortEstimated()) {
            return;
        }
        List<EffortEstimator.Load> history = entity.getKind() == null ? List.of()
                : workoutSessionRepository.findTop4ByUserIdAndKindAndStatusAndSessionIdNotOrderByStartedAtDesc(
                        entity.getUserId(), entity.getKind(), WorkoutSessionStatus.COMPLETED, entity.getSessionId())
                        .stream()
                        .map(EffortEstimator::loadOf)
                        .toList();
        Integer estimate = EffortEstimator.estimate(entity, history, newBest);
        entity.setPerceivedExertion(estimate);
        entity.setEffortEstimated(estimate != null);
    }

    @Override
    @Transactional
    public WorkoutSessionView cancelSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        validateTransitionAllowed(entity);
        endCancelled(entity, Instant.now(clock));
        return toSessionView(workoutSessionRepository.save(entity));
    }

    @Override
    @Transactional
    public WorkoutSessionView countSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        if (entity.getStatus() != WorkoutSessionStatus.CANCELLED) {
            throw new IllegalArgumentException("Only a cancelled workout session can be counted");
        }
        if (entity.getSetEntries().stream().noneMatch(exercise -> exercise.getSets().stream().anyMatch(this::isDoneSet))) {
            throw new BadRequestException(NOTHING_LOGGED_CODE, NOTHING_LOGGED_MESSAGE);
        }
        // Counted as it happened: it ends at its last done or skipped set, or when it was cancelled.
        Instant lastSet = entity.getSetEntries().stream()
                .flatMap(exercise -> exercise.getSets().stream())
                .map(WorkoutExerciseSetLogEntity::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant completedAt = lastSet != null && lastSet.isAfter(entity.getStartedAt()) ? lastSet : entity.getEndedAt();
        Completion completion = completeEntity(entity, completedAt);
        return toSessionView(completion.session()).withNewRecords(completion.newRecords());
    }

    @Override
    @Transactional
    public WorkoutSessionView updateEffort(CurrentUserPrincipal currentUser, UUID sessionId, SessionEffortRequest request) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        if (entity.getStatus() != WorkoutSessionStatus.COMPLETED) {
            throw new IllegalArgumentException("Only a completed workout session has an effort");
        }
        entity.setPerceivedExertion(request.perceivedExertion());
        entity.setEffortEstimated(false);
        return toSessionView(workoutSessionRepository.save(entity));
    }

    @Override
    @Transactional
    public WorkoutSessionView recordRestTaken(CurrentUserPrincipal currentUser,
                                              UUID sessionId,
                                              UUID exerciseLogId,
                                              int setNumber,
                                              SetRestRequest request) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        validateLiveEditable(entity);
        WorkoutExerciseSetLogEntity set = findExerciseLog(entity, exerciseLogId).getSets().stream()
                .filter(candidate -> candidate.getSetNumber() == setNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Workout set not found"));
        if (!isDoneSet(set)) {
            throw new IllegalArgumentException("Only a done set has a rest after it");
        }
        set.setRestTakenSeconds(request.restTakenSeconds());
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
        boolean replaced = session.getEntries()
                .removeIf(exercise -> exercise.isSet()
                        && exercise.getExercise() != null
                        && exercise.getExercise().getExerciseId().equals(request.exerciseId()));
        if (replaced) {
            // Hibernate writes inserts before orphan deletes, so the replaced row is removed first or the new
            // row collides with it on the (session, sequence) unique index.
            workoutSessionRepository.flush();
        }
        session.getEntries().add(toSessionExerciseLog(currentUser.userId(), session, request, true));
        validateSessionExerciseSequences(session);
        deriveSessionMetrics(session);
        // A free session starts without exercises. Its kind follows the exercises as they are added.
        applySessionKind(session);
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
        SessionEntry exercise = findExerciseLog(session, exerciseLogId);
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
        applySetLogRequest(setLog, request);
        deriveExerciseMetrics(exercise);
        deriveSessionMetrics(session);
        return toSessionView(workoutSessionRepository.save(session));
    }

    @Override
    @Transactional
    public void deleteSession(CurrentUserPrincipal currentUser, UUID sessionId) {
        WorkoutSessionEntity entity = findSession(currentUser.userId(), sessionId);
        boolean publishDelete = entity.getStatus() == WorkoutSessionStatus.COMPLETED;
        // Kept and marked: every read leaves it out from now on, and the event lets the projections drop it.
        entity.setDeletedAt(Instant.now(clock));
        workoutSessionRepository.save(entity);
        if (publishDelete) {
            publishWorkoutDeleted(entity);
        }
    }

    private void publishWorkoutDeleted(WorkoutSessionEntity entity) {
        domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", entity.getSessionId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutDeleted",
                1,
                Instant.now(clock),
                SOURCE,
                entity.getUserId(),
                new WorkoutDeletedPayload(entity.getSessionId(), entity.getPlanId(), Instant.now(clock))));
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
        if (row.status() == WorkoutSessionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(IMPORT_IN_PROGRESS_MESSAGE);
        }
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
        // Imported history cannot be asked again, so a SPORTS row that arrives without a sport
        // activity is stored as OTHER instead of being rejected.
        SportActivity sportActivity = row.workoutType() == WorkoutType.SPORTS && row.sportActivity() == null
                ? SportActivity.OTHER
                : row.sportActivity();
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
                row.exercises(),
                sportActivity,
                row.distanceKm());
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

        Map<UUID, WorkoutPlanDayEntity> copyDayBySourceDayId = new LinkedHashMap<>();
        for (WorkoutPlanDayEntity sourceDay : source.getDays()) {
            WorkoutPlanDayEntity copyDay = toDuplicatePlanDay(copy, sourceDay);
            copy.getDays().add(copyDay);
            copyDayBySourceDayId.put(sourceDay.getPlanDayId(), copyDay);
        }
        for (PlanEntry sourceExercise : source.getEntries()) {
            WorkoutPlanDayEntity copyDay = sourceExercise.getPlanDay() == null
                    ? null
                    : copyDayBySourceDayId.get(sourceExercise.getPlanDay().getPlanDayId());
            copy.getEntries().add(toDuplicatePlanExercise(copy, copyDay, sourceExercise));
        }
        return copy;
    }

    private String toCopyName(String sourceName) {
        int maxBaseLength = PLAN_NAME_MAX_LENGTH - COPY_SUFFIX.length();
        String baseName = sourceName.length() > maxBaseLength ? sourceName.substring(0, maxBaseLength) : sourceName;
        return baseName + COPY_SUFFIX;
    }

    private WorkoutPlanDayEntity toDuplicatePlanDay(WorkoutPlanEntity plan, WorkoutPlanDayEntity source) {
        WorkoutPlanDayEntity copy = new WorkoutPlanDayEntity();
        copy.setPlanDayId(UUID.randomUUID());
        copy.setPlan(plan);
        copy.setDayOfWeek(source.getDayOfWeek());
        copy.setKind(source.getKind());
        copy.setTitle(source.getTitle());
        copy.setSportActivity(source.getSportActivity());
        copy.setEstimatedDurationMinutes(source.getEstimatedDurationMinutes());
        return copy;
    }

    private PlanEntry toDuplicatePlanExercise(WorkoutPlanEntity plan,
                                                              WorkoutPlanDayEntity planDay,
                                                              PlanEntry source) {
        PlanEntry copy = new PlanEntry();
        copy.setPlanExerciseId(UUID.randomUUID());
        copy.setPlan(plan);
        copy.setPlanDay(planDay);
        copy.setExercise(source.getExercise());
        copy.setExerciseNameSnapshot(source.getExerciseNameSnapshot());
        copy.setEntryType(source.getEntryType());
        copy.setSequence(source.getSequence());
        copy.setPosition(source.getPosition());
        copy.setKind(source.getKind());
        copy.setLabel(source.getLabel());
        copy.setPlannedSeconds(source.getPlannedSeconds());
        copy.setRounds(source.getRounds());
        copy.setTargetSets(source.getTargetSets());
        copy.setTargetReps(source.getTargetReps());
        copy.setTargetWeightKg(source.getTargetWeightKg());
        copy.setTargetDurationSeconds(source.getTargetDurationSeconds());
        copy.setTargetDistanceKm(source.getTargetDistanceKm());
        copy.setRestSeconds(source.getRestSeconds());
        copy.setTempoSeconds(source.getTempoSeconds());
        copy.setNotes(source.getNotes());
        for (WorkoutPlanExerciseSetTargetEntity sourceSet : source.getSetTargets()) {
            copy.getSetTargets().add(toDuplicateSetTarget(copy, sourceSet));
        }
        return copy;
    }

    private WorkoutPlanExerciseSetTargetEntity toDuplicateSetTarget(PlanEntry exercise,
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
        repeated.setStartedAt(Instant.now(clock));
        repeated.setSportActivity(source.getSportActivity());
        repeated.setPlanDayId(source.getPlanDayId());
        for (SessionEntry sourceExercise : source.getSetEntries()) {
            List<WorkoutExerciseSetLogEntity> loggedSets = sourceExercise.getSets().stream()
                    .filter(this::isDoneSet)
                    .toList();
            if (!loggedSets.isEmpty()) {
                repeated.getEntries().add(toRepeatedExerciseLog(repeated, sourceExercise, loggedSets));
            }
        }
        applySessionKind(repeated);
        return repeated;
    }

    private SessionEntry toRepeatedExerciseLog(WorkoutSessionEntity session,
                                                           SessionEntry source,
                                                           List<WorkoutExerciseSetLogEntity> loggedSets) {
        SessionEntry repeated = new SessionEntry();
        repeated.setExerciseLogId(UUID.randomUUID());
        repeated.setSession(session);
        repeated.setExercise(source.getExercise());
        repeated.setExerciseNameSnapshot(source.getExerciseNameSnapshot());
        repeated.setSequence(source.getSequence());
        repeated.setTempoSeconds(source.getTempoSeconds());
        repeated.setNotes(source.getNotes());
        for (WorkoutExerciseSetLogEntity sourceSet : loggedSets) {
            repeated.getSets().add(toRepeatedSetLog(repeated, sourceSet));
        }
        return repeated;
    }

    private WorkoutExerciseSetLogEntity toRepeatedSetLog(SessionEntry exercise,
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
        entity.setSportActivity(request.workoutType() == WorkoutType.SPORTS ? request.sportActivity() : null);
        entity.setStatus(request.status());
        entity.setSource(request.source());
        entity.setStartedAt(request.startedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setDurationMinutes(request.durationMinutes());
        // An estimate stays one only while the value is unchanged; a value the person typed is theirs.
        entity.setCaloriesEstimated(entity.isCaloriesEstimated()
                && Objects.equals(entity.getCaloriesBurned(), request.caloriesBurned()));
        entity.setEffortEstimated(entity.isEffortEstimated()
                && Objects.equals(entity.getPerceivedExertion(), request.perceivedExertion()));
        entity.setCaloriesBurned(request.caloriesBurned());
        entity.setPerceivedExertion(request.perceivedExertion());
        entity.setNotes(request.notes());
        entity.setDistanceKm(request.distanceKm());
        replaceSessionExercises(userId, entity, request.exercises());
        applySessionKind(entity);
        deriveSessionMetrics(entity);
    }

    /**
     * Resolves and stores the session's kind from its workout type and the exercise types of
     * its exercise logs, in sequence order. Must run after the session's exercises are in their
     * final state and before the entity is first saved, since the kind column is not null.
     */
    private void applySessionKind(WorkoutSessionEntity session) {
        List<ExerciseType> exerciseTypesInOrder = session.getSetEntries().stream()
                .sorted(Comparator.comparing(SessionEntry::getSequence))
                .map(exercise -> exercise.getExercise() == null ? null : exercise.getExercise().getExerciseType())
                .toList();
        session.setKind(sessionKindResolver.resolve(session.getWorkoutType(), exerciseTypesInOrder));
    }

    /**
     * Replaces a plan's whole exercise content in one pass: the flat top-level exercises when the
     * plan has no week, or each day's own exercises when it does. Cross-field validation upstream
     * guarantees the two are never both non-empty at once.
     */
    private void replacePlanExercisesAndDays(UUID userId,
                                             WorkoutPlanEntity plan,
                                             List<WorkoutPlanExerciseInput> flatExercises,
                                             List<WorkoutPlanDayInput> dayInputs) {
        boolean hadExisting = !plan.getEntries().isEmpty();
        plan.getEntries().clear();
        if (hadExisting) {
            // Force the orphan-removal DELETEs to run BEFORE the replacement rows are inserted.
            // Without this flush, Hibernate orders the INSERTs before the DELETEs within a single
            // flush, so the new (plan_id, sequence) rows collide with the not-yet-deleted old ones
            // on uk_workout_plan_exercises_plan_sequence, and a stale day-scoped row could still
            // reference a plan day this same call is about to remove.
            workoutPlanRepository.flush();
        }

        replacePlanDays(plan, dayInputs);

        if (dayInputs == null || dayInputs.isEmpty()) {
            addPlanExercises(userId, plan, null, flatExercises);
            return;
        }
        Map<Integer, WorkoutPlanDayEntity> daysByDayOfWeek = plan.getDays().stream()
                .collect(Collectors.toMap(WorkoutPlanDayEntity::getDayOfWeek, day -> day));
        for (WorkoutPlanDayInput dayInput : dayInputs) {
            addPlanExercises(userId, plan, daysByDayOfWeek.get(dayInput.dayOfWeek()), dayInput.exercises());
        }
    }

    /**
     * Replaces a plan's week as a whole. A day is matched to its input by day of week: a retained
     * day keeps its planDayId, since sessions started from it refer to that id; a day no longer
     * present is removed; a new day of week gets a new one. Must run after the plan's exercises
     * have already been cleared (and flushed, if any existed), so a removed day is never still
     * referenced by an exercise row when its own DELETE runs.
     */
    private void replacePlanDays(WorkoutPlanEntity plan, List<WorkoutPlanDayInput> inputs) {
        List<WorkoutPlanDayInput> effectiveInputs = inputs == null ? List.of() : inputs;
        Map<Integer, WorkoutPlanDayEntity> existingByDayOfWeek = plan.getDays().stream()
                .collect(Collectors.toMap(WorkoutPlanDayEntity::getDayOfWeek, day -> day));
        Set<Integer> keptDaysOfWeek = effectiveInputs.stream().map(WorkoutPlanDayInput::dayOfWeek).collect(Collectors.toSet());

        boolean removedAny = plan.getDays().removeIf(day -> !keptDaysOfWeek.contains(day.getDayOfWeek()));
        if (removedAny) {
            // Same ordering hazard as the exercise flush above, for uk_workout_plan_days_plan_day:
            // run the removed days' DELETEs before any new (plan_id, day_of_week) row is inserted.
            workoutPlanRepository.flush();
        }

        for (WorkoutPlanDayInput input : effectiveInputs) {
            WorkoutPlanDayEntity day = existingByDayOfWeek.get(input.dayOfWeek());
            if (day == null) {
                day = new WorkoutPlanDayEntity();
                day.setPlanDayId(UUID.randomUUID());
                day.setPlan(plan);
                plan.getDays().add(day);
            }
            day.setDayOfWeek(input.dayOfWeek());
            day.setKind(input.kind());
            day.setTitle(input.title());
            day.setSportActivity(input.kind() == SessionKind.SPORT ? input.sportActivity() : null);
            day.setEstimatedDurationMinutes(input.estimatedDurationMinutes());
        }
    }

    /**
     * Adds exercises to the plan's single owning exercise collection, either flat (day is null) or
     * scoped to one day. The order of the exercises is unique within a day.
     */
    private void addPlanExercises(UUID userId,
                                  WorkoutPlanEntity plan,
                                  WorkoutPlanDayEntity day,
                                  List<WorkoutPlanExerciseInput> inputs) {
        if (inputs == null) {
            return;
        }
        for (WorkoutPlanExerciseInput input : inputs.stream().sorted(java.util.Comparator.comparing(WorkoutPlanExerciseInput::sequence)).toList()) {
            PlanEntry exercise = new PlanEntry();
            ExerciseEntity sourceExercise = findAccessibleExercise(userId, input.exerciseId());
            exercise.setPlanExerciseId(UUID.randomUUID());
            exercise.setPlan(plan);
            exercise.setPlanDay(day);
            exercise.setExercise(sourceExercise);
            exercise.setExerciseNameSnapshot(sourceExercise.getName());
            exercise.setSequence(input.sequence());
            exercise.setTargetSets(input.targetSets());
            exercise.setTargetReps(input.targetReps());
            exercise.setTargetWeightKg(input.targetWeightKg());
            exercise.setTargetDurationSeconds(input.targetDurationSeconds());
            exercise.setTargetDistanceKm(input.targetDistanceKm());
            exercise.setRestSeconds(input.restSeconds());
            exercise.setTempoSeconds(input.tempoSeconds());
            exercise.setNotes(input.notes());
            replaceSetTargets(exercise, input.setTargets());
            plan.getEntries().add(exercise);
        }
    }

    private void replaceSetTargets(PlanEntry exercise,
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
        // The rest taken after a set is measured by the exercise screen, never typed, so an edit that
        // rebuilds the sets keeps it for the same exercise and set number.
        Map<String, Integer> restTaken = new LinkedHashMap<>();
        for (SessionEntry exercise : session.getSetEntries()) {
            for (WorkoutExerciseSetLogEntity set : exercise.getSets()) {
                if (set.getRestTakenSeconds() != null && exercise.getExercise() != null) {
                    restTaken.putIfAbsent(exercise.getExercise().getExerciseId() + "#" + set.getSetNumber(), set.getRestTakenSeconds());
                }
            }
        }
        // Only the moves are replaced: an edit that sends exercises keeps the session's time segments.
        boolean hadExisting = session.getEntries().stream().anyMatch(SessionEntry::isSet);
        session.getEntries().removeIf(SessionEntry::isSet);
        if (hadExisting && session.getSessionId() != null && workoutSessionRepository.existsById(session.getSessionId())) {
            // As for plans: run the removals before the new rows go in, or a new (session, sequence)
            // row collides with the old one still in the table.
            workoutSessionRepository.flush();
        }
        if (inputs == null) {
            return;
        }
        for (WorkoutExerciseLogInput input : inputs.stream().sorted(java.util.Comparator.comparing(WorkoutExerciseLogInput::sequence)).toList()) {
            SessionEntry exercise = toSessionExerciseLog(userId, session, input, true);
            for (WorkoutExerciseSetLogEntity set : exercise.getSets()) {
                set.setRestTakenSeconds(restTaken.get(input.exerciseId() + "#" + set.getSetNumber()));
            }
            session.getEntries().add(exercise);
        }
    }

    private void deriveSessionMetrics(WorkoutSessionEntity session) {
        if (session.getStartedAt() != null && session.getCompletedAt() != null) {
            long minutes = Duration.between(session.getStartedAt(), session.getCompletedAt()).toMinutes();
            session.setDurationMinutes(Math.toIntExact(minutes));
        }
        session.getSetEntries().forEach(this::deriveExerciseMetrics);
        // Distances logged on the exercises are the measurement, so their sum replaces whatever the
        // session held and follows them as they change. A session without any, such as a sport
        // session, keeps the distance it was given.
        BigDecimal distanceFromExercises = sumExerciseDistance(session);
        if (distanceFromExercises != null) {
            session.setDistanceKm(distanceFromExercises);
        }
    }

    /** The sum of the distances of the session's exercise logs, or null when none has one. */
    private BigDecimal sumExerciseDistance(WorkoutSessionEntity session) {
        BigDecimal total = null;
        for (SessionEntry exercise : session.getSetEntries()) {
            if (exercise.getDistanceKm() != null) {
                total = (total == null ? BigDecimal.ZERO : total).add(exercise.getDistanceKm());
            }
        }
        return total;
    }

    private void deriveExerciseMetrics(SessionEntry exercise) {
        List<WorkoutExerciseSetLogEntity> loggedSets = exercise.getSets().stream()
                .filter(this::isDoneSet)
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

    private SessionEntry toSessionExerciseLog(UUID userId,
                                                          WorkoutSessionEntity session,
                                                          WorkoutExerciseLogInput input,
                                                          boolean logged) {
        SessionEntry exercise = new SessionEntry();
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
        exercise.setTempoSeconds(input.tempoSeconds());
        exercise.setNotes(input.notes());
        replaceSetLogs(exercise, input.sets(), logged);
        deriveExerciseMetrics(exercise);
        return exercise;
    }

    private void replaceSetLogs(SessionEntry exercise,
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

    private SessionEntry toSeededExerciseLog(WorkoutSessionEntity session,
                                                         PlanEntry planExercise) {
        SessionEntry exercise = new SessionEntry();
        exercise.setExerciseLogId(UUID.randomUUID());
        exercise.setSession(session);
        exercise.setExercise(planExercise.getExercise());
        exercise.setExerciseNameSnapshot(planExercise.getExerciseNameSnapshot());
        exercise.setEntryType(planExercise.getEntryType());
        exercise.setSequence(planExercise.getSequence());
        exercise.setPosition(planExercise.getPosition());
        exercise.setTempoSeconds(planExercise.getTempoSeconds());
        exercise.setNotes(planExercise.getNotes());
        if (!planExercise.isSet()) {
            exercise.setKind(planExercise.getKind());
            exercise.setLabel(planExercise.getLabel());
            exercise.setPlannedSeconds(planExercise.getPlannedSeconds());
            exercise.setStatus(WorkoutSetStatus.PENDING);
            return exercise;
        }

        for (WorkoutPlanExerciseSetTargetEntity setTarget : planExercise.getSetTargets()) {
            exercise.getSets().add(toSeededSetLog(exercise, setTarget));
        }
        // A plan written with only a number of sets (as the coach writes them) still gives the
        // workout that many sets, each with the exercise's own targets.
        if (planExercise.getSetTargets().isEmpty() && planExercise.getTargetSets() != null) {
            for (int setNumber = 1; setNumber <= planExercise.getTargetSets(); setNumber++) {
                exercise.getSets().add(toSeededSetLog(exercise, planExercise, setNumber));
            }
        }
        return exercise;
    }

    private WorkoutExerciseSetLogEntity toSeededSetLog(SessionEntry exercise,
                                                       PlanEntry planExercise,
                                                       int setNumber) {
        WorkoutExerciseSetLogEntity setLog = new WorkoutExerciseSetLogEntity();
        setLog.setSetLogId(UUID.randomUUID());
        setLog.setExerciseLog(exercise);
        setLog.setSetNumber(setNumber);
        setLog.setReps(planExercise.getTargetReps());
        setLog.setWeightKg(planExercise.getTargetWeightKg());
        setLog.setDurationSeconds(planExercise.getTargetDurationSeconds());
        setLog.setDistanceKm(planExercise.getTargetDistanceKm());
        setLog.setRestSeconds(planExercise.getRestSeconds());
        setLog.setLogged(false);
        return setLog;
    }

    private WorkoutExerciseSetLogEntity toSeededSetLog(SessionEntry exercise,
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

    private SessionEntry findExerciseLog(WorkoutSessionEntity session, UUID exerciseLogId) {
        return session.getEntries().stream()
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
        if (logged) {
            setLog.setUpdatedAt(Instant.now(clock));
        }
    }

    private void applySetLogRequest(WorkoutExerciseSetLogEntity setLog,
                                    UpsertWorkoutExerciseSetRequest request) {
        setLog.setReps(request.reps());
        setLog.setWeightKg(request.weightKg());
        setLog.setDurationSeconds(request.durationSeconds());
        setLog.setDistanceKm(request.distanceKm());
        setLog.setRestSeconds(request.restSeconds());
        setLog.setPerceivedExertion(request.perceivedExertion());
        setLog.setNotes(request.notes());
        setLog.setStatus(resolveLiveSetStatus(request.status()));
        setLog.setUpdatedAt(Instant.now(clock));
    }

    private WorkoutSetStatus resolveLiveSetStatus(WorkoutSetStatus status) {
        if (status == null) {
            return WorkoutSetStatus.DONE;
        }
        if (status == WorkoutSetStatus.DONE || status == WorkoutSetStatus.SKIPPED) {
            return status;
        }
        throw new IllegalArgumentException("Set status must be DONE or SKIPPED");
    }

    private boolean isDoneSet(WorkoutExerciseSetLogEntity setLog) {
        return setLog.getStatus() == WorkoutSetStatus.DONE && setLog.isLogged();
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
        // A second of leeway: the database rounds a start it was given up to its own precision, so a
        // workout started and completed in the same instant could otherwise seem to start in the future.
        if (startedAt.isAfter(Instant.now(clock).plusSeconds(1))) {
            throw new IllegalArgumentException("startedAt must not be in the future");
        }
        if (completedAt != null && completedAt.isAfter(Instant.now(clock).plusSeconds(1))) {
            throw new IllegalArgumentException("completedAt must not be in the future");
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

    private void validatePlanExerciseList(List<WorkoutPlanExerciseInput> exercises) {
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

    /**
     * Cross-field rules for a plan's week, answered with a fixed message that never echoes the
     * request. A day's own exercise sequence numbers only have to be unique within that day, so
     * they are checked the same way a flat plan's are, one day at a time.
     */
    private void validatePlanWeek(List<WorkoutPlanExerciseInput> flatExercises, List<WorkoutPlanDayInput> dayInputs) {
        List<WorkoutPlanDayInput> days = dayInputs == null ? List.of() : dayInputs;
        if (!days.isEmpty() && flatExercises != null && !flatExercises.isEmpty()) {
            throw new IllegalArgumentException(WEEK_EXCLUSIVE_EXERCISES_MESSAGE);
        }
        validateUniqueNumbers(days.stream().map(WorkoutPlanDayInput::dayOfWeek).toList(), DUPLICATE_DAY_MESSAGE);
        for (WorkoutPlanDayInput day : days) {
            if (day.kind() == SessionKind.SPORT && day.sportActivity() == null) {
                throw new IllegalArgumentException(SPORT_DAY_NEEDS_ACTIVITY_MESSAGE);
            }
            if (day.kind() == SessionKind.SPORT && !day.exercises().isEmpty()) {
                throw new IllegalArgumentException(SPORT_DAY_HAS_NO_EXERCISES_MESSAGE);
            }
            validatePlanExerciseList(day.exercises());
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
                session.getEntries().stream()
                        .map(SessionEntry::getSequence)
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
        if (isAfterWhenBothPresent(from, to)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    private void validateHistoryRange(Instant from, Instant to) {
        if (isAfterWhenBothPresent(from, to)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (from != null
                && to != null
                && Duration.between(from, to).compareTo(Duration.ofDays(exerciseHistoryMaxRangeDays)) > 0) {
            throw new IllegalArgumentException("Exercise history range must not exceed "
                    + exerciseHistoryMaxRangeDays + " days");
        }
    }

    private boolean isAfterWhenBothPresent(Instant from, Instant to) {
        return from != null && to != null && from.isAfter(to);
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
        publishPersonalRecords(session, findNewRecords(session));
    }

    /** The records of one exercise that a session beat. */
    private record ExerciseRecords(SessionEntry exercise,
                                   String exerciseName,
                                   List<PersonalRecordAchievedPayload.RecordAchievement> achievements) {
    }

    /** Each exercise of the session that set a personal record, against the person's other sessions. */
    private List<ExerciseRecords> findNewRecords(WorkoutSessionEntity session) {
        List<ExerciseRecords> found = new ArrayList<>();
        for (SessionEntry exercise : session.getSetEntries()) {
            PersonalRecordSnapshot current = snapshotExercise(exercise);
            if (current.isEmpty()) {
                continue;
            }
            PersonalRecordSnapshot prior = snapshotRows(workoutExerciseLogRepository.findRecordRows(
                    session.getUserId(),
                    exercise.getExercise().getExerciseId(),
                    session.getSessionId()));
            List<PersonalRecordAchievedPayload.RecordAchievement> achievements = compareRecords(current, prior);
            if (!achievements.isEmpty()) {
                found.add(new ExerciseRecords(exercise, exercise.getExerciseNameSnapshot(), achievements));
            }
        }
        return found;
    }

    private void publishPersonalRecords(WorkoutSessionEntity session, List<ExerciseRecords> records) {
        for (ExerciseRecords record : records) {
            SessionEntry exercise = record.exercise();
            List<PersonalRecordAchievedPayload.RecordAchievement> achievements = record.achievements();
            domainEventPublisher.publish(workoutEventsTopic(), "WorkoutSession", session.getSessionId().toString(), new FitSyncDomainEvent<>(
                    UUID.randomUUID(),
                    "PersonalRecordAchieved",
                    1,
                    Instant.now(clock),
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

    private PersonalRecordSnapshot snapshotExercise(SessionEntry exercise) {
        List<ExerciseRecordSetRow> rows = exercise.getSets().stream()
                .filter(this::isDoneSet)
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
                Instant.now(clock),
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
                Instant.now(clock),
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
                        payload.source(),
                        payload.caloriesEstimated())));
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
                session.getSetEntries().size(),
                session.getSource().name(),
                session.isCaloriesEstimated());
    }

    /**
     * The catalog image list is ordered by position and batch-fetched, so taking the first entry
     * here does not reintroduce a per-exercise query.
     */
    private String primaryImageUrl(com.fitsync.workout.entity.ExerciseEntity exercise) {
        if (exercise == null || exercise.getImages().isEmpty()) {
            return null;
        }
        return exercise.getImages().get(0).getUrl();
    }

    private WorkoutPlanView toPlanView(WorkoutPlanEntity entity) {
        List<PlanEntry> flatExercises = new ArrayList<>();
        Map<UUID, List<PlanEntry>> dayExercisesByDayId = new LinkedHashMap<>();
        for (PlanEntry exercise : entity.getSetEntries()) {
            if (exercise.getPlanDay() == null) {
                flatExercises.add(exercise);
            } else {
                dayExercisesByDayId.computeIfAbsent(exercise.getPlanDay().getPlanDayId(), id -> new ArrayList<>())
                        .add(exercise);
            }
        }

        List<WorkoutPlanDayView> days = entity.getDays().stream()
                .sorted(Comparator.comparing(WorkoutPlanDayEntity::getDayOfWeek))
                .map(day -> toPlanDayView(day, dayExercisesByDayId.getOrDefault(day.getPlanDayId(), List.of())))
                .toList();

        return new WorkoutPlanView(
                entity.getPlanId(),
                entity.getName(),
                entity.getDescription(),
                entity.getGoalFocus(),
                entity.getWorkoutType(),
                entity.getDifficulty(),
                entity.getEstimatedDurationMinutes(),
                entity.getStatus(),
                flatExercises.stream().map(this::toPlanExerciseView).toList(),
                entity.getDateTimeCreated(),
                entity.getDateTimeUpdated(),
                days,
                days.isEmpty() ? null : days.size());
    }

    private WorkoutPlanDayView toPlanDayView(WorkoutPlanDayEntity day, List<PlanEntry> exercises) {
        return new WorkoutPlanDayView(
                day.getPlanDayId(),
                day.getDayOfWeek(),
                day.getKind(),
                day.getTitle(),
                day.getSportActivity(),
                day.getSportActivity() == null ? null : day.getSportActivity().label(),
                day.getEstimatedDurationMinutes(),
                exercises.stream()
                        .sorted(Comparator.comparing(PlanEntry::getSequence))
                        .map(this::toPlanExerciseView)
                        .toList());
    }

    private WorkoutPlanExerciseView toPlanExerciseView(PlanEntry exercise) {
        return new WorkoutPlanExerciseView(
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
                primaryImageUrl(exercise.getExercise()),
                exercise.getSetTargets().stream()
                        .map(setTarget -> new WorkoutPlanExerciseSetTargetView(
                                setTarget.getSetTargetId(),
                                setTarget.getSetNumber(),
                                setTarget.getTargetReps(),
                                setTarget.getTargetWeightKg(),
                                setTarget.getTargetDurationSeconds(),
                                setTarget.getTargetDistanceKm(),
                                setTarget.getRestSeconds()))
                        .toList(),
                exercise.getTempoSeconds());
    }

    private WorkoutSessionResumeView toResumeView(WorkoutSessionEntity session) {
        int doneSets = 0;
        int skippedSets = 0;
        int pendingSets = 0;
        WorkoutResumeSetView nextSet = null;

        List<SessionEntry> exercises = session.getSetEntries().stream()
                .sorted(Comparator.comparing(SessionEntry::getSequence))
                .toList();
        for (SessionEntry exercise : exercises) {
            List<WorkoutExerciseSetLogEntity> sets = exercise.getSets().stream()
                    .sorted(Comparator.comparing(WorkoutExerciseSetLogEntity::getSetNumber))
                    .toList();
            for (WorkoutExerciseSetLogEntity setLog : sets) {
                if (setLog.getStatus() == WorkoutSetStatus.DONE) {
                    doneSets++;
                } else if (setLog.getStatus() == WorkoutSetStatus.SKIPPED) {
                    skippedSets++;
                } else {
                    pendingSets++;
                    if (nextSet == null && isResumable(session)) {
                        nextSet = toResumeSetView(exercise, setLog);
                    }
                }
            }
        }

        return new WorkoutSessionResumeView(
                session.getSessionId(),
                session.getStatus(),
                doneSets,
                skippedSets,
                pendingSets,
                doneSets + skippedSets + pendingSets,
                nextSet);
    }

    private boolean isResumable(WorkoutSessionEntity session) {
        return session.getStatus() == WorkoutSessionStatus.PLANNED
                || session.getStatus() == WorkoutSessionStatus.IN_PROGRESS;
    }

    private WorkoutResumeSetView toResumeSetView(SessionEntry exercise,
                                                 WorkoutExerciseSetLogEntity setLog) {
        return new WorkoutResumeSetView(
                exercise.getExerciseLogId(),
                exercise.getExercise().getExerciseId(),
                exercise.getExerciseNameSnapshot(),
                exercise.getSequence(),
                setLog.getSetLogId(),
                setLog.getSetNumber(),
                setLog.getStatus(),
                setLog.getReps(),
                setLog.getWeightKg(),
                setLog.getDurationSeconds(),
                setLog.getDistanceKm(),
                setLog.getRestSeconds(),
                setLog.getPerceivedExertion(),
                setLog.getNotes());
    }

    private WorkoutSessionView toSessionView(WorkoutSessionEntity entity) {
        List<WorkoutExerciseLogView> exercises = entity.getSetEntries().stream()
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
                                        setLog.getStatus(),
                                        setLog.getReps(),
                                        setLog.getWeightKg(),
                                        setLog.getDurationSeconds(),
                                        setLog.getDistanceKm(),
                                        setLog.getRestSeconds(),
                                        setLog.getPerceivedExertion(),
                                        setLog.isLogged(),
                                        setLog.getNotes(),
                                        setLog.getRestTakenSeconds()))
                                .toList(),
                        exercise.getTempoSeconds()))
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
                entity.getDateTimeUpdated(),
                entity.getKind(),
                entity.getSportActivity(),
                entity.getSportActivity() == null ? null : entity.getSportActivity().label(),
                entity.getDistanceKm(),
                entity.getPlanDayId(),
                entity.getAutoClosedAt(),
                entity.getEndedAt(),
                entity.isCaloriesEstimated(),
                entity.isEffortEstimated(),
                List.of());
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
