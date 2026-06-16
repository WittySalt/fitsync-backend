package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsUserMetricDateKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private LocalDate metricDate;

    public AnalyticsUserMetricDateKey() {
    }

    public AnalyticsUserMetricDateKey(UUID userId, LocalDate metricDate) {
        this.userId = userId;
        this.metricDate = metricDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnalyticsUserMetricDateKey other)) {
            return false;
        }
        return Objects.equals(userId, other.userId)
                && Objects.equals(metricDate, other.metricDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, metricDate);
    }
}
