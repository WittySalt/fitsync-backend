package com.fitsync.notification.domain;

/** Who asked for an email. */
public enum EmailTrigger {
    /** Decided by the service from a domain event or the planner. */
    EVENT,
    /** Asked for by an admin from the console. */
    ADMIN
}
