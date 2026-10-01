package com.fitsync.workout.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * One short step of an exercise guide. Read-only: the rows are written by a migration and keyed by
 * the exercise code, so they survive the catalogue import recreating catalogue rows.
 */
@Entity
@Immutable
@Table(name = "exercise_short_steps")
public class ExerciseShortStepEntity {

    @EmbeddedId
    private Key id;

    @Column(name = "step_text", nullable = false, length = 200)
    private String text;

    protected ExerciseShortStepEntity() {
    }

    public Key getId() { return id; }
    public String getText() { return text; }

    @Embeddable
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "exercise_code", nullable = false, length = 100)
        private String exerciseCode;

        @Column(name = "step_number", nullable = false)
        private Integer stepNumber;

        protected Key() {
        }

        public String getExerciseCode() { return exerciseCode; }
        public Integer getStepNumber() { return stepNumber; }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(exerciseCode, key.exerciseCode) && Objects.equals(stepNumber, key.stepNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exerciseCode, stepNumber);
        }
    }
}
