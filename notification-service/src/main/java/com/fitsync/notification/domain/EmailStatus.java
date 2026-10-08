package com.fitsync.notification.domain;

public enum EmailStatus {
    PENDING,
    SENT,
    /** No longer true or no longer wanted when its time came, so it was never sent. */
    DROPPED,
    /** SES refused it on every attempt. */
    FAILED
}
