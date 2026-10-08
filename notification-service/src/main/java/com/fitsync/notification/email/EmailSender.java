package com.fitsync.notification.email;

/**
 * Hands one email to whatever delivers it. The body is plain text; an implementation that can send
 * HTML builds it from the text with {@link EmailHtml}, so both versions always say the same thing.
 */
public interface EmailSender {

    /**
     * Sends one email, or throws when it could not be handed over, so the caller can retry it later.
     *
     * @param message what to send and where
     */
    void send(OutgoingEmail message);
}
