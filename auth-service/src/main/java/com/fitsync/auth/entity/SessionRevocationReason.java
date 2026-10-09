package com.fitsync.auth.entity;

/** Why a session was revoked, stored in {@code auth_sessions.revoked_reason}. */
public enum SessionRevocationReason {
    /** The person signed out on that device. */
    LOGOUT,
    /** The person revoked it from the session list on another (or the same) device. */
    REVOKED_BY_USER,
    /** The person revoked every other session. */
    REVOKED_OTHERS,
    /** A refresh token that had already been rotated was presented again, so the token may be stolen. */
    REFRESH_TOKEN_REUSE,
    /** The account was scheduled for deletion. */
    ACCOUNT_DELETION
}
