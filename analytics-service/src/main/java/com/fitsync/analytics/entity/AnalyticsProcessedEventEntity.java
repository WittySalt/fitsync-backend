package com.fitsync.analytics.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per consumed event id, so a redelivered event is skipped. Also holds the deleted-meal markers of
 * {@code MealNutritionProjectionService}, keyed by meal id.
 */
@Entity
@Table(name = "analytics_processed_events")
public class AnalyticsProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
