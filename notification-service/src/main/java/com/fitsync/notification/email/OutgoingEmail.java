package com.fitsync.notification.email;

import java.util.UUID;

import com.fitsync.notification.domain.EmailKind;

/**
 * One email ready to leave the service. The id and kind travel with it only so that a sender can
 * log them; the address and the body must never be logged.
 *
 * @param emailId  the record the email belongs to
 * @param kind     which email of the spec it is
 * @param to       the recipient address
 * @param subject  the subject line
 * @param bodyText the plain-text body
 */
public record OutgoingEmail(UUID emailId, EmailKind kind, String to, String subject, String bodyText) {

    @Override
    public String toString() {
        // The generated record string would print the address and the body into any log it reaches.
        return "OutgoingEmail[emailId=" + emailId + ", kind=" + kind + "]";
    }
}
