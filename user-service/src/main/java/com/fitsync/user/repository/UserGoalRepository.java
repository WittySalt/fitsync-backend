package com.fitsync.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.entity.UserGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGoalRepository extends JpaRepository<UserGoalEntity, UUID> {

    Optional<UserGoalEntity> findByUserIdAndGoalType(UUID userId, GoalType goalType);

    List<UserGoalEntity> findByUserIdOrderByGoalTypeAsc(UUID userId);

    List<UserGoalEntity> findByUserIdAndStatusOrderByGoalTypeAsc(UUID userId, GoalStatus status);

    List<UserGoalEntity> findByStatusOrderByUserIdAscGoalTypeAsc(GoalStatus status);

    void deleteByUserId(UUID userId);
}
