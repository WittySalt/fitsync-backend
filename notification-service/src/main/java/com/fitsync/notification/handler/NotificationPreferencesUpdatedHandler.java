package com.fitsync.notification.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.service.RecipientStore;

/**
 * Replaces every setting at once, because the event carries the whole current state. The
 * dispatcher reads these settings again when an email's time comes, so a switch turned off after
 * an email was queued still stops it.
 */
@Service
public class NotificationPreferencesUpdatedHandler
        implements DomainEventConsumer<NotificationPreferencesUpdatedPayload> {

    private final RecipientStore recipients;

    public NotificationPreferencesUpdatedHandler(RecipientStore recipients) {
        this.recipients = recipients;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<NotificationPreferencesUpdatedPayload> event) {
        if (event.userId() == null || event.payload() == null) {
            return;
        }
        NotificationPreferencesUpdatedPayload payload = event.payload();
        RecipientEntity recipient = recipients.getOrCreate(event.userId());
        recipient.setEmailEnabled(payload.emailEnabled());
        recipient.setWorkoutReminderEnabled(payload.workoutReminderEnabled());
        recipient.setGoalProgressEnabled(payload.goalProgressReminderEnabled());
        recipient.setWeeklyReportEnabled(payload.weeklyProgressReportEnabled());
        recipient.setWeeklyReportChannel(Text.upper(payload.weeklyProgressReportChannel(), 20));
        recipient.setQuietHoursStart(Text.time(payload.quietHoursStart()));
        recipient.setQuietHoursEnd(Text.time(payload.quietHoursEnd()));
        recipient.setTimezone(Text.clip(payload.timezone(), 64));
        recipient.setPreferredWorkoutTime(Text.time(payload.preferredWorkoutTime()));
        recipients.save(recipient);
    }
}
