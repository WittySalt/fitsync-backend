package com.fitsync.notification.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_plan_days")
@IdClass(PlanDayEntity.Key.class)
public class PlanDayEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "exercise_count", nullable = false)
    private int exerciseCount;

    protected PlanDayEntity() {
    }

    public PlanDayEntity(UUID userId, int dayOfWeek, String kind, String title,
                         Integer estimatedMinutes, int exerciseCount) {
        this.userId = userId;
        this.dayOfWeek = dayOfWeek;
        this.kind = kind;
        this.title = title;
        this.estimatedMinutes = estimatedMinutes;
        this.exerciseCount = exerciseCount;
    }

    public UUID getUserId() { return userId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public int getExerciseCount() { return exerciseCount; }

    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        private UUID userId;
        private int dayOfWeek;

        public Key() {
        }

        public Key(UUID userId, int dayOfWeek) {
            this.userId = userId;
            this.dayOfWeek = dayOfWeek;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return dayOfWeek == key.dayOfWeek && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, dayOfWeek);
        }
    }
}
