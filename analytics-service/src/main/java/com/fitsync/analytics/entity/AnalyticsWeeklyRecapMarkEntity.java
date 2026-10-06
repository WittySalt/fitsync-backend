package com.fitsync.analytics.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * A week whose recap was emitted for a user. Written once and never changed: a new row is always
 * inserted, so a run that raced another one fails on the key instead of emitting twice.
 */
@Entity
@Table(name = "analytics_weekly_recap_marks")
@IdClass(AnalyticsWeeklyRecapMarkKey.class)
public class AnalyticsWeeklyRecapMarkEntity implements Persistable<AnalyticsWeeklyRecapMarkKey> {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "emitted_at", nullable = false)
    private Instant emittedAt;

    @Transient
    private boolean stored;

    public AnalyticsWeeklyRecapMarkEntity() {
    }

    public AnalyticsWeeklyRecapMarkEntity(UUID userId, LocalDate weekStart, Instant emittedAt) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.emittedAt = emittedAt;
    }

    @Override
    public AnalyticsWeeklyRecapMarkKey getId() {
        return new AnalyticsWeeklyRecapMarkKey(userId, weekStart);
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.stored = true;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public Instant getEmittedAt() {
        return emittedAt;
    }
}
