package com.fitsync.workout.repository;

import java.util.List;

import com.fitsync.workout.entity.ExerciseShortStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseShortStepRepository extends JpaRepository<ExerciseShortStepEntity, ExerciseShortStepEntity.Key> {

    /** The short steps of one exercise in step order, matched case-sensitively on the stored code. */
    @Query("""
            select step.text from ExerciseShortStepEntity step
            where step.id.exerciseCode = :code
            order by step.id.stepNumber asc
            """)
    List<String> findTextsByExerciseCode(@Param("code") String code);
}
