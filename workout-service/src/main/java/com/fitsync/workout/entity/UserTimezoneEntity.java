package com.fitsync.workout.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The IANA zone of a person's profile, as last projected from a user-service event. */
@Entity
@Table(name = "workout_user_timezones")
public class UserTimezoneEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserTimezoneEntity() {
    }

    public UserTimezoneEntity(UUID userId, String timezone, Instant updatedAt) {
        this.userId = userId;
        this.timezone = timezone;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void change(String timezone, Instant updatedAt) {
        this.timezone = timezone;
        this.updatedAt = updatedAt;
    }
}
