package com.fitsync.nutrition.messaging;

import java.math.RoundingMode;
import java.time.Instant;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the local projection of the user's nutrition targets in step with GoalUpdated events from
 * user-service. Only calorie and macro goals are tracked; any other goal type is ignored. A goal that
 * is no longer ACTIVE clears its target and date but keeps the goal id.
 */
@Service
public class GoalUpdatedEventConsumerImpl implements DomainEventConsumer<GoalUpdatedPayload> {

    private static final String ACTIVE = "ACTIVE";
    private static final String CALORIES = "CALORIES";
    private static final String PROTEIN = "PROTEIN";
    /** Carbohydrate goals arrive under either name. */
    private static final String CARBS = "CARBS";
    private static final String CARBOHYDRATES = "CARBOHYDRATES";
    private static final String FAT = "FAT";

    private final NutritionTargetRepository nutritionTargetRepository;

    public GoalUpdatedEventConsumerImpl(NutritionTargetRepository nutritionTargetRepository) {
        this.nutritionTargetRepository = nutritionTargetRepository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<GoalUpdatedPayload> event) {
        GoalUpdatedPayload payload = event.payload();
        if (!CALORIES.equals(payload.goalType())
                && !PROTEIN.equals(payload.goalType())
                && !CARBS.equals(payload.goalType())
                && !CARBOHYDRATES.equals(payload.goalType())
                && !FAT.equals(payload.goalType())) {
            return;
        }

        NutritionTargetEntity entity = nutritionTargetRepository.findById(event.userId())
                .orElseGet(() -> {
                    NutritionTargetEntity created = new NutritionTargetEntity();
                    created.setUserId(event.userId());
                    return created;
                });

        boolean active = ACTIVE.equals(payload.status());
        if (CALORIES.equals(payload.goalType())) {
            entity.setCalorieGoalId(payload.goalId());
            // Rounded to the nearest kcal rather than cut off, so a 1999.6 kcal goal is 2000, not 1999.
            entity.setCalorieTarget(active && payload.targetValue() != null
                    ? payload.targetValue().setScale(0, RoundingMode.HALF_UP).intValue() : null);
            entity.setCalorieTargetDate(active ? payload.targetDate() : null);
        } else if (PROTEIN.equals(payload.goalType())) {
            entity.setProteinGoalId(payload.goalId());
            entity.setProteinTarget(active ? payload.targetValue() : null);
            entity.setProteinTargetDate(active ? payload.targetDate() : null);
        } else if (CARBS.equals(payload.goalType()) || CARBOHYDRATES.equals(payload.goalType())) {
            entity.setCarbGoalId(payload.goalId());
            entity.setCarbTargetGrams(active ? payload.targetValue() : null);
            entity.setCarbTargetDate(active ? payload.targetDate() : null);
        } else {
            entity.setFatGoalId(payload.goalId());
            entity.setFatTargetGrams(active ? payload.targetValue() : null);
            entity.setFatTargetDate(active ? payload.targetDate() : null);
        }
        entity.setProjectionUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        nutritionTargetRepository.save(entity);
    }
}
