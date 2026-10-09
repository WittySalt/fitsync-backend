package com.fitsync.notification.admin;

/** Why an admin cannot send an email now, as a stable code and a sentence for the console. */
public enum AdminEmailRefusal {
    NO_ADDRESS("This user has no email address on record"),
    DELETION_PENDING("This user's account is scheduled for deletion"),
    EMAIL_OFF("This user has email notifications off"),
    WORKOUT_REMINDERS_OFF("This user has Workout reminders off"),
    GOAL_PROGRESS_OFF("This user has Goal progress reminders off"),
    WEEKLY_REPORT_OFF("This user has the Weekly progress report off"),
    WEEKLY_REPORT_NOT_EMAIL("This user gets the weekly report in the app, not by email"),
    NO_SESSION_TODAY("This user's plan has no session today"),
    WORKOUT_LOGGED_TODAY("This user has already logged a workout today"),
    NO_WEIGHT_GOAL("This user has no active weight goal"),
    NO_WEIGHT("This user has no weight recorded"),
    GOAL_NOT_REACHED("This user's latest weight has not reached the weight goal"),
    NO_RECAP("There is no recap for last week");

    private final String message;

    AdminEmailRefusal(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
