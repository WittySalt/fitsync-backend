package com.fitsync.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_guide_state")
public class UserGuideStateEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "state", nullable = false, columnDefinition = "text")
    private String state;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserGuideStateEntity() {
    }

    public UserGuideStateEntity(UUID userId, String state, Instant updatedAt) {
        this.userId = userId;
        this.state = state;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
