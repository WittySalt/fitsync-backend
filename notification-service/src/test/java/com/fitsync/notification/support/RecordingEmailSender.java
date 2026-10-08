package com.fitsync.notification.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fitsync.notification.email.EmailSender;
import com.fitsync.notification.email.OutgoingEmail;

/** Keeps every email handed to it, or refuses them all to stand in for an SES outage. */
public class RecordingEmailSender implements EmailSender {

    private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failing = new AtomicBoolean();

    @Override
    public void send(OutgoingEmail message) {
        if (failing.get()) {
            throw new IllegalStateException("SES unavailable");
        }
        sent.add(message);
    }

    public List<OutgoingEmail> sent() {
        return List.copyOf(sent);
    }

    public void failing(boolean value) {
        failing.set(value);
    }

    public void reset() {
        sent.clear();
        failing.set(false);
    }
}
