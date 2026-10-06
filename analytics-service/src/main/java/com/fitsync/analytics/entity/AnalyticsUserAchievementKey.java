package com.fitsync.analytics.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AnalyticsUserAchievementKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String code;

    public AnalyticsUserAchievementKey() {
    }

    public AnalyticsUserAchievementKey(UUID userId, String code) {
        this.userId = userId;
        this.code = code;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnalyticsUserAchievementKey other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, code);
    }
}
