package com.fitsync.user.entity;

import java.time.LocalTime;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreferencesEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "workout_reminder_enabled", nullable = false)
    private boolean workoutReminderEnabled;

    @Column(name = "meal_reminder_enabled", nullable = false)
    private boolean mealReminderEnabled;

    @Column(name = "weigh_in_reminder_enabled", nullable = false)
    private boolean weighInReminderEnabled;

    @Column(name = "goal_progress_reminder_enabled", nullable = false)
    private boolean goalProgressReminderEnabled;

    @Column(name = "weekly_progress_report_enabled", nullable = false)
    private boolean weeklyProgressReportEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_progress_report_channel", nullable = false, length = 20)
    private NotificationChannel weeklyProgressReportChannel = NotificationChannel.EMAIL;

    @Column(name = "marketing_emails_enabled", nullable = false)
    private boolean marketingEmailsEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isPushEnabled() { return pushEnabled; }
    public void setPushEnabled(boolean pushEnabled) { this.pushEnabled = pushEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isWorkoutReminderEnabled() { return workoutReminderEnabled; }
    public void setWorkoutReminderEnabled(boolean workoutReminderEnabled) { this.workoutReminderEnabled = workoutReminderEnabled; }
    public boolean isMealReminderEnabled() { return mealReminderEnabled; }
    public void setMealReminderEnabled(boolean mealReminderEnabled) { this.mealReminderEnabled = mealReminderEnabled; }
    public boolean isWeighInReminderEnabled() { return weighInReminderEnabled; }
    public void setWeighInReminderEnabled(boolean weighInReminderEnabled) { this.weighInReminderEnabled = weighInReminderEnabled; }
    public boolean isGoalProgressReminderEnabled() { return goalProgressReminderEnabled; }
    public void setGoalProgressReminderEnabled(boolean goalProgressReminderEnabled) { this.goalProgressReminderEnabled = goalProgressReminderEnabled; }
    public boolean isWeeklyProgressReportEnabled() { return weeklyProgressReportEnabled; }
    public void setWeeklyProgressReportEnabled(boolean weeklyProgressReportEnabled) { this.weeklyProgressReportEnabled = weeklyProgressReportEnabled; }
    public NotificationChannel getWeeklyProgressReportChannel() { return weeklyProgressReportChannel; }
    public void setWeeklyProgressReportChannel(NotificationChannel weeklyProgressReportChannel) { this.weeklyProgressReportChannel = weeklyProgressReportChannel; }
    public boolean isMarketingEmailsEnabled() { return marketingEmailsEnabled; }
    public void setMarketingEmailsEnabled(boolean marketingEmailsEnabled) { this.marketingEmailsEnabled = marketingEmailsEnabled; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
