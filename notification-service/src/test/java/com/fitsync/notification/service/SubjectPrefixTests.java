package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.fitsync.notification.config.NotificationProperties;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.support.NotificationTestBase;

/** Staging marks its emails with a subject prefix; the stored subject stays as composed. */
@TestPropertySource(properties = "fitsync.notification.email.subject-prefix=[Staging]")
class SubjectPrefixTests extends NotificationTestBase {

    @Test
    void prefixAndOneSpaceGoInFrontOfTheSubjectAsTheEmailLeaves() {
        register("ada@example.com", "Ada");
        requestDeletion(clock.instant(), Instant.parse("2026-11-06T03:00:00Z"));

        tick.run();

        assertThat(sender.sent()).singleElement().satisfies(email ->
                assertThat(email.subject()).isEqualTo("[Staging] Your account is scheduled for deletion"));
        assertThat(emailRepository.findAll()).singleElement().satisfies(email -> {
            assertThat(email.getKind()).isEqualTo(EmailKind.DELETION_SCHEDULED);
            assertThat(email.getSubject()).isEqualTo("Your account is scheduled for deletion");
        });
    }

    @Test
    void blankPrefixLeavesTheSubjectAlone() {
        NotificationProperties.Email none = new NotificationProperties.Email(true, "a@b.c", "x", null, "  ");
        NotificationProperties.Email unset = new NotificationProperties.Email(true, "a@b.c", "x", null, null);
        NotificationProperties.Email staging = new NotificationProperties.Email(true, "a@b.c", "x", null, " [Staging] ");

        assertThat(none.subject("Weight goal reached: 70 kg")).isEqualTo("Weight goal reached: 70 kg");
        assertThat(unset.subject("Weight goal reached: 70 kg")).isEqualTo("Weight goal reached: 70 kg");
        assertThat(staging.subject("Weight goal reached: 70 kg")).isEqualTo("[Staging] Weight goal reached: 70 kg");
        assertThat(unset.senderName()).isEqualTo("FitSync");
    }
}
