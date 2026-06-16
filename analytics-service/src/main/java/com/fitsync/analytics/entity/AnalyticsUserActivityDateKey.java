package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsUserActivityDateKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private LocalDate activityDate;

    public AnalyticsUserActivityDateKey() {
    }

    public AnalyticsUserActivityDateKey(UUID userId, LocalDate activityDate) {
        this.userId = userId;
        this.activityDate = activityDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnalyticsUserActivityDateKey other)) {
            return false;
        }
        return Objects.equals(userId, other.userId)
                && Objects.equals(activityDate, other.activityDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, activityDate);
    }
}
