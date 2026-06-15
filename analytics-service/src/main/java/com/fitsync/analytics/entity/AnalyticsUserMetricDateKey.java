package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsUserMetricDateKey implements Serializable {

    private UUID userId;
    private LocalDate metricDate;

    public AnalyticsUserMetricDateKey() {
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
