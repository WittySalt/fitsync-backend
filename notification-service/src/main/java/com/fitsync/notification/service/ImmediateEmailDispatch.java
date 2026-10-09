package com.fitsync.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends an email that is due as soon as the transaction that queued it commits, so a security notice
 * does not wait for the next tick. Nothing happens if that transaction rolls back, and the queued row
 * stays the record: the dispatcher reads it under a row lock and applies the same rules as the tick,
 * quiet hours and retries included. The tick still plans reminders and retries what failed here.
 */
@Component
public class ImmediateEmailDispatch {

    private final EmailDispatcher dispatcher;

    public ImmediateEmailDispatch(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDueEmailQueued(DueEmailQueued event) {
        dispatcher.dispatchNow(event.emailId());
    }
}
