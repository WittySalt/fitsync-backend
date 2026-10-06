package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsWeeklyRecapMarkKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private LocalDate weekStart;

    public AnalyticsWeeklyRecapMarkKey() {
    }

    public AnalyticsWeeklyRecapMarkKey(UUID userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnalyticsWeeklyRecapMarkKey other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(weekStart, other.weekStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, weekStart);
    }
}
