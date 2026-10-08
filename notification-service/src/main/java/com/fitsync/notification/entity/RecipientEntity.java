package com.fitsync.notification.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_recipients")
public class RecipientEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "workout_reminder_enabled", nullable = false)
    private boolean workoutReminderEnabled;

    @Column(name = "goal_progress_enabled", nullable = false)
    private boolean goalProgressEnabled;

    @Column(name = "weekly_report_enabled", nullable = false)
    private boolean weeklyReportEnabled;

    @Column(name = "weekly_report_channel", length = 20)
    private String weeklyReportChannel;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "preferred_workout_time")
    private LocalTime preferredWorkoutTime;

    @Column(name = "deletion_scheduled_for")
    private Instant deletionScheduledFor;

    @Column(name = "last_workout_at")
    private Instant lastWorkoutAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecipientEntity() {
    }

    public RecipientEntity(UUID userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isWorkoutReminderEnabled() { return workoutReminderEnabled; }
    public void setWorkoutReminderEnabled(boolean value) { this.workoutReminderEnabled = value; }
    public boolean isGoalProgressEnabled() { return goalProgressEnabled; }
    public void setGoalProgressEnabled(boolean value) { this.goalProgressEnabled = value; }
    public boolean isWeeklyReportEnabled() { return weeklyReportEnabled; }
    public void setWeeklyReportEnabled(boolean value) { this.weeklyReportEnabled = value; }
    public String getWeeklyReportChannel() { return weeklyReportChannel; }
    public void setWeeklyReportChannel(String value) { this.weeklyReportChannel = value; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime value) { this.quietHoursStart = value; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime value) { this.quietHoursEnd = value; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public LocalTime getPreferredWorkoutTime() { return preferredWorkoutTime; }
    public void setPreferredWorkoutTime(LocalTime value) { this.preferredWorkoutTime = value; }
    public Instant getDeletionScheduledFor() { return deletionScheduledFor; }
    public void setDeletionScheduledFor(Instant value) { this.deletionScheduledFor = value; }
    public Instant getLastWorkoutAt() { return lastWorkoutAt; }
    public void setLastWorkoutAt(Instant value) { this.lastWorkoutAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
