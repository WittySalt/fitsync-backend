package com.fitsync.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code fitsync.notification}.
 *
 * @param email        how email leaves the service
 * @param appBaseUrl   the SPA's address in this environment, where every link in an email points
 * @param tick         how often reminders are planned and due emails are sent
 */
@ConfigurationProperties(prefix = "fitsync.notification")
public record NotificationProperties(Email email, String appBaseUrl, Duration tick) {

    public NotificationProperties {
        email = email == null ? new Email(false, "no-reply@fitsync.local", "ap-southeast-1", null, null) : email;
        appBaseUrl = appBaseUrl == null || appBaseUrl.isBlank()
                ? "http://localhost:4200"
                : appBaseUrl.replaceAll("/+$", "");
        tick = tick == null ? Duration.ofMinutes(5) : tick;
    }

    /**
     * @param enabled true sends through SES; false writes each email to the log instead
     * @param sender  the From address, an identity verified in SES
     * @param region  the SES region
     * @param senderName    the display name beside the From address; blank sends the bare address
     * @param subjectPrefix put with one space before every subject (staging uses {@code [Staging]});
     *                      blank adds nothing
     */
    public record Email(boolean enabled, String sender, String region, String senderName, String subjectPrefix) {

        public Email {
            senderName = senderName == null ? "FitSync" : senderName.strip();
            subjectPrefix = subjectPrefix == null ? "" : subjectPrefix.strip();
        }

        /** The subject as it goes out: the prefix and one space in front, or unchanged without one. */
        public String subject(String subject) {
            return subjectPrefix.isEmpty() ? subject : subjectPrefix + " " + subject;
        }
    }
}
