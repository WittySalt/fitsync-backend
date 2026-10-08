package com.fitsync.notification.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload.PlannedDay;
import com.fitsync.notification.entity.PlanDayEntity;
import com.fitsync.notification.repository.PlanDayRepository;

/**
 * Replaces the user's weekly template with the active plan's, as the event carries it whole. No
 * days means no active plan, and with no days stored E1 has nothing to remind about.
 */
@Service
public class WorkoutPlanScheduleChangedHandler implements DomainEventConsumer<WorkoutPlanScheduleChangedPayload> {

    private static final String DEFAULT_TITLE = "Training";

    private final PlanDayRepository planDays;

    public WorkoutPlanScheduleChangedHandler(PlanDayRepository planDays) {
        this.planDays = planDays;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<WorkoutPlanScheduleChangedPayload> event) {
        if (event.userId() == null || event.payload() == null) {
            return;
        }
        planDays.deleteByUserId(event.userId());
        // One row per weekday; a malformed day is skipped and a repeated weekday keeps its last entry.
        Map<Integer, PlanDayEntity> byDay = new LinkedHashMap<>();
        for (PlannedDay day : event.payload().days()) {
            if (day == null || day.dayOfWeek() < 1 || day.dayOfWeek() > 7 || day.kind() == null) {
                continue;
            }
            String title = Text.clip(day.title(), 120);
            byDay.put(day.dayOfWeek(), new PlanDayEntity(event.userId(), day.dayOfWeek(),
                    Text.upper(day.kind(), 20), title == null ? DEFAULT_TITLE : title,
                    day.estimatedMinutes(), Math.max(0, day.exerciseCount())));
        }
        planDays.saveAll(byDay.values());
    }
}
