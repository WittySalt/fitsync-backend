package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsUserActivityDateKey implements Serializable {

    private UUID userId;
    private LocalDate activityDate;

    public AnalyticsUserActivityDateKey() {
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
