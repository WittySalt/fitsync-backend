package com.fitsync.analytics.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How many recipes the user has cooked, and when the first and the last cook finished. */
@Entity
@Table(name = "analytics_user_cooks")
public class AnalyticsUserCooksEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "cooks", nullable = false)
    private Integer cooks;

    @Column(name = "first_cook_at")
    private Instant firstCookAt;

    @Column(name = "last_cook_at")
    private Instant lastCookAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getCooks() {
        return cooks;
    }

    public void setCooks(Integer cooks) {
        this.cooks = cooks;
    }

    public Instant getFirstCookAt() {
        return firstCookAt;
    }

    public void setFirstCookAt(Instant firstCookAt) {
        this.firstCookAt = firstCookAt;
    }

    public Instant getLastCookAt() {
        return lastCookAt;
    }

    public void setLastCookAt(Instant lastCookAt) {
        this.lastCookAt = lastCookAt;
    }
}
