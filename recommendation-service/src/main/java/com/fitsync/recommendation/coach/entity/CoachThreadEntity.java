package com.fitsync.recommendation.coach.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One conversation between a user and the coach.
 *
 * <p>The thread is the unit a user sees and manages; the turns underneath it are the unit the
 * context window is assembled from. Archiving rather than deleting keeps the telemetry on its
 * turns available for the evaluation harness after a user has tidied their list away.
 */
@Entity
@Table(name = "coach_thread")
public class CoachThreadEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * A short label for the thread list. Derived from the opening question rather than asked for,
     * because a user who has to name a conversation before having it will not start one.
     */
    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    /** The page the thread was started from, or null when it was started without one. */
    @Column(name = "surface", length = 20)
    private String surface;

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }

    public UUID getThreadId() {
        return threadId;
    }

    public void setThreadId(UUID threadId) {
        this.threadId = threadId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
