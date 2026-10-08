package com.fitsync.workout.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload.PlannedDay;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.PlanEntry;
import com.fitsync.workout.entity.WorkoutPlanDayEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.messaging.DomainEventPublisher;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes {@code WorkoutPlanScheduleChanged} for notification-service, which reminds a user on
 * the days their active plan trains. The event always carries the whole week of the plan that is
 * active at that moment, or no plan and no days, so the consumer replaces what it holds.
 */
@Service
public class WorkoutPlanScheduleEventService {

    static final String EVENT_TYPE = "WorkoutPlanScheduleChanged";
    private static final Logger log = LoggerFactory.getLogger(WorkoutPlanScheduleEventService.class);
    private static final String AGGREGATE_TYPE = "WorkoutPlanSchedule";
    private static final String SOURCE = "workout-service";
    private static final int BATCH_SIZE = 200;

    private final String workoutEventsTopic;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    private final TransactionTemplate userTransactionTemplate;
    private final boolean catchUpOnStartup;

    public WorkoutPlanScheduleEventService(@Value("${fitsync.kafka.topics.workout-events:workout-events}") String workoutEventsTopic,
                                           WorkoutPlanRepository workoutPlanRepository,
                                           OutboxEventRepository outboxEventRepository,
                                           DomainEventPublisher domainEventPublisher,
                                           Clock clock,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${fitsync.workout.plan-schedule.catch-up-on-startup:true}")
                                           boolean catchUpOnStartup) {
        this.workoutEventsTopic = workoutEventsTopic;
        this.workoutPlanRepository = workoutPlanRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
        this.userTransactionTemplate = new TransactionTemplate(transactionManager);
        this.userTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.catchUpOnStartup = catchUpOnStartup;
    }

    /**
     * Publishes the schedule of the plan that is active for the user now. It runs inside the
     * caller's transaction, after the plan change, so the event commits or rolls back with it. The
     * lookup flushes the pending plan changes first, so a plan archived or demoted by this same
     * transaction is no longer seen as active.
     */
    public void publishActiveSchedule(UUID userId) {
        WorkoutPlanScheduleChangedPayload payload = activeSchedule(userId, Instant.now(clock));
        publish(userId, UUID.randomUUID(), payload);
    }

    /**
     * Publishes once for every user with an active plan, so notification-service learns the
     * schedules that were set before it existed. The event id is derived from the schedule itself,
     * and a schedule already on the outbox under that id is skipped, so a restart emits nothing new
     * unless a plan changed since the last catch-up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!catchUpOnStartup) {
            return;
        }
        try {
            int published = catchUpActiveSchedules();
            log.info("Workout plan schedule catch-up finished published={}", published);
        } catch (RuntimeException exception) {
            // A failed catch-up must not stop the service; each later plan change publishes anyway.
            log.warn("Workout plan schedule catch-up failed exceptionType={}", exception.getClass().getName());
        }
    }

    int catchUpActiveSchedules() {
        int published = 0;
        int page = 0;
        Slice<UUID> userIds;
        do {
            userIds = workoutPlanRepository.findUserIdsByStatus(WorkoutPlanStatus.ACTIVE, PageRequest.of(page++, BATCH_SIZE));
            for (UUID userId : userIds.getContent()) {
                Boolean emitted = userTransactionTemplate.execute(status -> catchUpUser(userId));
                if (Boolean.TRUE.equals(emitted)) {
                    published++;
                }
            }
        } while (userIds.hasNext());
        return published;
    }

    private boolean catchUpUser(UUID userId) {
        WorkoutPlanScheduleChangedPayload payload = activeSchedule(userId, Instant.now(clock));
        UUID eventId = scheduleEventId(userId, payload);
        if (outboxEventRepository.existsById(eventId)) {
            return false;
        }
        publish(userId, eventId, payload);
        return true;
    }

    private WorkoutPlanScheduleChangedPayload activeSchedule(UUID userId, Instant changedAt) {
        Optional<WorkoutPlanEntity> active = workoutPlanRepository
                .findByUserIdAndStatusOrderByDateTimeUpdatedDesc(userId, WorkoutPlanStatus.ACTIVE)
                .stream()
                .findFirst();
        if (active.isEmpty()) {
            return new WorkoutPlanScheduleChangedPayload(null, null, List.of(), changedAt);
        }
        WorkoutPlanEntity plan = active.get();
        // Every day row is sent, rest days included; the consumer decides which days remind.
        List<PlannedDay> days = plan.getDays().stream()
                .sorted(Comparator.comparing(WorkoutPlanDayEntity::getDayOfWeek))
                .map(day -> new PlannedDay(
                        day.getDayOfWeek(),
                        day.getKind() == null ? null : day.getKind().name(),
                        day.getTitle(),
                        day.getEstimatedDurationMinutes(),
                        exerciseCount(plan, day)))
                .toList();
        return new WorkoutPlanScheduleChangedPayload(plan.getPlanId(), plan.getName(), days, changedAt);
    }

    /**
     * Counts the moves of a day from the plan's own entries, which a plan edit keeps current, rather
     * than from the day's inverse collection, which is not refreshed within the editing transaction.
     */
    private static int exerciseCount(WorkoutPlanEntity plan, WorkoutPlanDayEntity day) {
        return (int) plan.getEntries().stream()
                .filter(PlanEntry::isSet)
                .filter(entry -> entry.getPlanDay() != null
                        && day.getPlanDayId() != null
                        && day.getPlanDayId().equals(entry.getPlanDay().getPlanDayId()))
                .count();
    }

    /** The same user and schedule always give the same id; the time of the change is left out. */
    static UUID scheduleEventId(UUID userId, WorkoutPlanScheduleChangedPayload payload) {
        WorkoutPlanScheduleChangedPayload timeless = new WorkoutPlanScheduleChangedPayload(
                payload.planId(), payload.planName(), payload.days(), null);
        String key = EVENT_TYPE + "|" + userId + "|" + timeless;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private void publish(UUID userId, UUID eventId, WorkoutPlanScheduleChangedPayload payload) {
        domainEventPublisher.publish(workoutEventsTopic, AGGREGATE_TYPE, userId.toString(), new FitSyncDomainEvent<>(
                eventId, EVENT_TYPE, 1, payload.changedAt(), SOURCE, userId, payload));
    }
}
