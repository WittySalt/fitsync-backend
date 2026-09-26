package com.fitsync.recommendation.coach.stream;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fitsync.recommendation.coach.orchestrator.CoachProgressEvent;
import com.fitsync.recommendation.coach.orchestrator.CoachProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Writes a turn's progress to one client over server-sent events.
 *
 * <p>This exists because {@link SseEmitter} is neither thread safe nor forgiving, and a coach turn
 * is both concurrent and long lived. Planned tools report from the coach's pool while the turn
 * itself reports from its own thread, so sends are serialised here. And once a client has gone the
 * emitter throws on every subsequent write, which would otherwise turn a closed browser tab into a
 * failed turn: after the first failure this stops writing and lets the turn finish, because the
 * answer is still being persisted and the user will see it when they return.
 *
 * <p>Event names are the contract the frontend subscribes to: {@code tool_started},
 * {@code tool_finished}, {@code thinking}, {@code answer} and {@code error}.
 */
public class SseProgressStream implements CoachProgressListener {

    private static final Logger LOG = LoggerFactory.getLogger(SseProgressStream.class);

    private final SseEmitter emitter;
    private final Object lock = new Object();
    private final AtomicBoolean clientGone = new AtomicBoolean();

    public SseProgressStream(SseEmitter emitter) {
        this.emitter = emitter;
        // A timeout or an error is the client's end of the connection going away, not a failure of
        // the work, so both are recorded the same way.
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(failure -> clientGone.set(true));
        emitter.onCompletion(() -> clientGone.set(true));
    }

    @Override
    public void onEvent(CoachProgressEvent event) {
        send(eventName(event.kind()), event);
    }

    /** The finished answer, sent last. */
    public void sendAnswer(Object answer) {
        send("answer", answer);
    }

    /**
     * Tells the client the turn failed, then closes.
     *
     * <p>The message is deliberately generic. Whatever went wrong is in the logs with a stack
     * trace; putting it on the wire would only hand a client the internals of a failure it cannot
     * act on.
     */
    public void sendFailure() {
        send("error", "The coach could not finish that turn.");
        complete();
    }

    public void complete() {
        synchronized (lock) {
            if (clientGone.get()) {
                return;
            }
            try {
                emitter.complete();
            } catch (RuntimeException ex) {
                LOG.debug("Coach progress stream was already closed", ex);
            }
            // Completing ends the connection either way, so nothing more may be written.
            clientGone.set(true);
        }
    }

    private void send(String name, Object payload) {
        if (clientGone.get()) {
            return;
        }
        synchronized (lock) {
            if (clientGone.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(name).data(payload));
            } catch (IOException | RuntimeException ex) {
                // Expected whenever someone closes the tab mid-answer. Debug, not warn.
                LOG.debug("Coach progress stream closed by the client", ex);
                clientGone.set(true);
            }
        }
    }

    private static String eventName(CoachProgressEvent.Kind kind) {
        return switch (kind) {
            case TOOL_STARTED -> "tool_started";
            case TOOL_FINISHED -> "tool_finished";
            case THINKING -> "thinking";
        };
    }
}
