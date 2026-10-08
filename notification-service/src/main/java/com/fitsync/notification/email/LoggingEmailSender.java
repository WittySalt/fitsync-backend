package com.fitsync.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used wherever SES is off, which is every local run and every test. It writes the email's id,
 * kind and subject to the log so a developer can see that an email would have gone out. The
 * address and the body are left out because both may carry personal data.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutgoingEmail message) {
        // The subject can contain a session title the user typed, so line breaks are taken out
        // inline before it reaches the log (CRLF log injection, CWE-117).
        String subject = String.valueOf(message.subject()).replace("\r", "_").replace("\n", "_");
        log.info("event=email_logged_not_sent emailId={} kind={} subject={}",
                message.emailId(), message.kind(), subject);
    }
}
