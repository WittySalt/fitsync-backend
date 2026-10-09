package com.fitsync.notification.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fitsync.notification.email.EmailSender;
import com.fitsync.notification.email.OutgoingEmail;

/** Keeps every email handed to it, or refuses them all to stand in for an SES outage. */
public class RecordingEmailSender implements EmailSender {

    private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failing = new AtomicBoolean();
    private final AtomicReference<Consumer<OutgoingEmail>> onSend = new AtomicReference<>();

    @Override
    public void send(OutgoingEmail message) {
        Consumer<OutgoingEmail> hook = onSend.get();
        if (hook != null) {
            hook.accept(message);
        }
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

    /** Runs inside every send, before it is recorded, so a test can act while a send is in flight. */
    public void onSend(Consumer<OutgoingEmail> hook) {
        onSend.set(hook);
    }

    public void reset() {
        onSend.set(null);
        sent.clear();
        failing.set(false);
    }
}
