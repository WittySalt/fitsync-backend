package com.fitsync.notification.handler;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.notification.entity.GoalEntity;
import com.fitsync.notification.entity.WeightEntity;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.WeightRepository;

/**
 * Keeps each goal as user-service last published it. The first time a goal is seen it also takes
 * its start: the moment, and the latest weight known at that moment. The start weight is what says
 * whether a weight goal points down or up, and it never changes after that.
 */
@Service
public class GoalUpdatedHandler implements DomainEventConsumer<GoalUpdatedPayload> {

    private final GoalRepository goals;
    private final WeightRepository weights;
    private final Clock clock;

    public GoalUpdatedHandler(GoalRepository goals, WeightRepository weights, Clock clock) {
        this.goals = goals;
        this.weights = weights;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<GoalUpdatedPayload> event) {
        GoalUpdatedPayload payload = event.payload();
        if (event.userId() == null || payload == null || payload.goalId() == null || payload.goalType() == null) {
            return;
        }
        Instant at = event.occurredAt() != null ? event.occurredAt() : clock.instant();
        GoalEntity goal = goals.findById(payload.goalId()).orElseGet(() -> {
            GoalEntity created = new GoalEntity(payload.goalId(), event.userId(), at);
            weights.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(event.userId(), at)
                    .map(WeightEntity::getWeightKg)
                    .ifPresent(created::setStartWeightKg);
            return created;
        });
        goal.setGoalType(Text.upper(payload.goalType(), 40));
        goal.setTargetValue(payload.targetValue());
        String status = Text.upper(payload.status(), 20);
        goal.setStatus(status == null ? "ACTIVE" : status);
        goal.setUpdatedAt(at);
        goals.save(goal);
    }
}
