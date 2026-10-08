package com.fitsync.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import com.fitsync.user.messaging.DomainEventPublisher;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publishes {@code NotificationPreferencesUpdated} for notification-service. The facts it carries
 * live in three rows (the notification toggles and quiet hours, the profile timezone and the
 * preferred training time), so a caller takes a {@link #snapshot} before it writes and calls
 * {@link #publishIfChanged} afterwards. Comparing whole snapshots means a save that changed only
 * unrelated fields, such as the display name or the water goal, puts nothing on the outbox.
 */
@Service
public class NotificationPreferencesEventService {

    static final String EVENT_TYPE = "NotificationPreferencesUpdated";
    private static final String USER_EVENTS_TOPIC = "user-events";
    private static final String AGGREGATE_TYPE = "UserNotificationPreferences";
    private static final String SOURCE = "user-service";
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm");

    private final UserNotificationPreferencesRepository userNotificationPreferencesRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTrainingPreferencesRepository userTrainingPreferencesRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    @Autowired
    public NotificationPreferencesEventService(UserNotificationPreferencesRepository userNotificationPreferencesRepository,
                                               UserProfileRepository userProfileRepository,
                                               UserTrainingPreferencesRepository userTrainingPreferencesRepository,
                                               DomainEventPublisher domainEventPublisher) {
        this(userNotificationPreferencesRepository, userProfileRepository, userTrainingPreferencesRepository,
                domainEventPublisher, Clock.systemUTC());
    }

    NotificationPreferencesEventService(UserNotificationPreferencesRepository userNotificationPreferencesRepository,
                                        UserProfileRepository userProfileRepository,
                                        UserTrainingPreferencesRepository userTrainingPreferencesRepository,
                                        DomainEventPublisher domainEventPublisher,
                                        Clock clock) {
        this.userNotificationPreferencesRepository = userNotificationPreferencesRepository;
        this.userProfileRepository = userProfileRepository;
        this.userTrainingPreferencesRepository = userTrainingPreferencesRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * The state the event would carry now, or null while the user has no notification row, since
     * the toggles cannot be stated before that row exists.
     */
    public NotificationPreferencesUpdatedPayload snapshot(UUID userId) {
        UserNotificationPreferencesEntity notifications =
                userNotificationPreferencesRepository.findById(userId).orElse(null);
        if (notifications == null) {
            return null;
        }
        String timezone = userProfileRepository.findById(userId)
                .map(UserProfileEntity::getTimezone)
                .map(String::trim)
                .filter(zone -> !zone.isEmpty())
                .orElse(null);
        LocalTime preferredWorkoutTime = userTrainingPreferencesRepository.findById(userId)
                .map(UserTrainingPreferencesEntity::getPreferredWorkoutTime)
                .orElse(null);
        return new NotificationPreferencesUpdatedPayload(
                notifications.isEmailEnabled(),
                notifications.isWorkoutReminderEnabled(),
                notifications.isGoalProgressReminderEnabled(),
                notifications.isWeeklyProgressReportEnabled(),
                notifications.getWeeklyProgressReportChannel() == null
                        ? null
                        : notifications.getWeeklyProgressReportChannel().name(),
                format(notifications.getQuietHoursStart()),
                format(notifications.getQuietHoursEnd()),
                timezone,
                format(preferredWorkoutTime));
    }

    /**
     * Publishes the current state when it differs from {@code before}. A null {@code before} means
     * the notification row did not exist yet, so a row created by this write is always published.
     *
     * @return true when an event was put on the outbox
     */
    public boolean publishIfChanged(UUID userId, NotificationPreferencesUpdatedPayload before) {
        NotificationPreferencesUpdatedPayload after = snapshot(userId);
        if (after == null || after.equals(before)) {
            return false;
        }
        publish(userId, after);
        return true;
    }

    /**
     * Publishes the current state unconditionally, for the admin republish of person facts.
     *
     * @return true when an event was put on the outbox, false when the user has no notification row
     */
    public boolean publishCurrent(UUID userId) {
        NotificationPreferencesUpdatedPayload current = snapshot(userId);
        if (current == null) {
            return false;
        }
        publish(userId, current);
        return true;
    }

    private void publish(UUID userId, NotificationPreferencesUpdatedPayload payload) {
        domainEventPublisher.publish(
                USER_EVENTS_TOPIC, AGGREGATE_TYPE, userId.toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(), EVENT_TYPE, 1, Instant.now(clock), SOURCE, userId, payload));
    }

    private static String format(LocalTime time) {
        return time == null ? null : time.format(HOURS_MINUTES);
    }
}
