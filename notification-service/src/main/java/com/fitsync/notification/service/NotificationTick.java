package com.fitsync.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One pass of the notification clock: plan today's reminders, then send whatever is due. Planning
 * runs first so a reminder decided in this pass can go out in the same pass. A failure while
 * planning must not hold back emails that are already due, so it is logged and the pass goes on.
 */
@Service
public class NotificationTick {

    private static final Logger log = LoggerFactory.getLogger(NotificationTick.class);

    private final TrainingReminderPlanner planner;
    private final EmailDispatcher dispatcher;

    public NotificationTick(TrainingReminderPlanner planner, EmailDispatcher dispatcher) {
        this.planner = planner;
        this.dispatcher = dispatcher;
    }

    public void run() {
        try {
            planner.planDue();
        } catch (RuntimeException ex) {
            log.error("event=training_reminder_planning_failed exceptionClass={}", ex.getClass().getName(), ex);
        }
        dispatcher.dispatchDue();
    }
}
