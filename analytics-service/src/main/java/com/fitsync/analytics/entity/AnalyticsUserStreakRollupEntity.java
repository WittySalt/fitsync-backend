package com.fitsync.analytics.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_user_streak_rollup")
public class AnalyticsUserStreakRollupEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "best_streak_days", nullable = false)
    private Integer bestStreakDays;

    @Column(name = "best_streak_from")
    private LocalDate bestStreakFrom;

    @Column(name = "best_streak_to")
    private LocalDate bestStreakTo;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getBestStreakDays() {
        return bestStreakDays;
    }

    public void setBestStreakDays(Integer bestStreakDays) {
        this.bestStreakDays = bestStreakDays;
    }

    public LocalDate getBestStreakFrom() {
        return bestStreakFrom;
    }

    public void setBestStreakFrom(LocalDate bestStreakFrom) {
        this.bestStreakFrom = bestStreakFrom;
    }

    public LocalDate getBestStreakTo() {
        return bestStreakTo;
    }

    public void setBestStreakTo(LocalDate bestStreakTo) {
        this.bestStreakTo = bestStreakTo;
    }

    public LocalDate getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(LocalDate lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
