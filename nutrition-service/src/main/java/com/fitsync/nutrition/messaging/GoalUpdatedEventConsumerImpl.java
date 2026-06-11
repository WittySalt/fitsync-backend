package com.fitsync.nutrition.messaging;

import java.time.Instant;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalUpdatedEventConsumerImpl implements GoalUpdatedEventConsumer {

    private static final String ACTIVE = "ACTIVE";
    private static final String CALORIES = "CALORIES";
    private static final String PROTEIN = "PROTEIN";
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
            entity.setCalorieTarget(active && payload.targetValue() != null ? payload.targetValue().intValue() : null);
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
