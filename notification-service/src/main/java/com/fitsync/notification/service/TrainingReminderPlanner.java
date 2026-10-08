package com.fitsync.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.PlanDayEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.repository.PlanDayRepository;
import com.fitsync.notification.repository.RecipientRepository;

/**
 * Decides E1 on every tick. A reminder goes out an hour before the preferred training time, or at
 * 08:00 when none is set, on a day the active plan trains and no workout was logged yet. It is only
 * worth sending until the training starts, so that time is its expiry; without a training time it
 * lasts until the local day ends. The local date is the dedupe key, which allows one per day.
 */
@Service
public class TrainingReminderPlanner {

    private static final LocalTime DEFAULT_SEND_TIME = LocalTime.of(8, 0);
    private static final String REST = "REST";

    private final RecipientRepository recipients;
    private final PlanDayRepository planDays;
    private final EmailQueue queue;
    private final EmailComposer composer;
    private final Clock clock;

    public TrainingReminderPlanner(RecipientRepository recipients, PlanDayRepository planDays, EmailQueue queue,
                                   EmailComposer composer, Clock clock) {
        this.recipients = recipients;
        this.planDays = planDays;
        this.queue = queue;
        this.composer = composer;
        this.clock = clock;
    }

    /** @return how many reminders were queued */
    @Transactional
    public int planDue() {
        Instant now = clock.instant();
        int queued = 0;
        for (RecipientEntity recipient : recipients.findByEmailEnabledTrueAndWorkoutReminderEnabledTrue()) {
            if (recipient.getEmail() != null && recipient.getDeletionScheduledFor() == null
                    && planFor(recipient, now)) {
                queued++;
            }
        }
        return queued;
    }

    private boolean planFor(RecipientEntity recipient, Instant now) {
        ZoneId zone = RecipientTime.zoneOf(recipient);
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate today = localNow.toLocalDate();
        PlanDayEntity day = planDays.findByUserIdAndDayOfWeek(recipient.getUserId(), today.getDayOfWeek().getValue())
                .orElse(null);
        if (day == null || REST.equals(day.getKind()) || RecipientTime.workedOutOn(recipient, today, zone)) {
            return false;
        }

        LocalTime trainingTime = recipient.getPreferredWorkoutTime();
        Instant sendAt;
        Instant expiresAt;
        if (trainingTime == null) {
            sendAt = today.atTime(DEFAULT_SEND_TIME).atZone(zone).toInstant();
            expiresAt = today.plusDays(1).atStartOfDay(zone).toInstant();
        } else {
            // Training before 01:00 would put the send time on the day before, so it goes at midnight.
            LocalTime sendTime = trainingTime.isBefore(LocalTime.of(1, 0))
                    ? LocalTime.MIDNIGHT
                    : trainingTime.minusHours(1);
            sendAt = today.atTime(sendTime).atZone(zone).toInstant();
            expiresAt = today.atTime(trainingTime).atZone(zone).toInstant();
        }
        if (now.isBefore(sendAt) || !now.isBefore(expiresAt)) {
            return false;
        }
        return queue.queue(recipient.getUserId(), EmailKind.TRAINING_TODAY, today.toString(),
                composer.trainingToday(recipient, day, trainingTime), sendAt, expiresAt);
    }
}
