package com.fitsync.notification.admin;

import com.fitsync.notification.domain.EmailKind;

/**
 * The emails an admin may send now, by the names the console uses. The deletion notice is not
 * here on purpose: it goes out only for a real deletion request.
 */
public enum AdminEmailKind {
    TRAINING_REMINDER(EmailKind.TRAINING_TODAY),
    GOAL_REACHED(EmailKind.GOAL_REACHED),
    WEEKLY_REPORT(EmailKind.WEEKLY_REPORT);

    private final EmailKind emailKind;

    AdminEmailKind(EmailKind emailKind) {
        this.emailKind = emailKind;
    }

    public EmailKind emailKind() {
        return emailKind;
    }
}
