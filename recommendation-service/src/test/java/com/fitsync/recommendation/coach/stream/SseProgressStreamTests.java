package com.fitsync.recommendation.coach.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fitsync.recommendation.coach.orchestrator.CoachProgressEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The progress stream, which exists to stop two ordinary situations from becoming failures.
 *
 * <p>A user closing the tab while the coach is still reading is not an error. Neither is two tools
 * finishing at the same moment on the planned path, although {@link SseEmitter} is not safe to call
 * from two threads at once.
 */
class SseProgressStreamTests {

    @Test
    @DisplayName("each kind of event goes out under the name the frontend subscribes to")
    void sendsEventsUnderTheirContractedNames() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        SseProgressStream stream = new SseProgressStream(emitter);

        stream.onEvent(CoachProgressEvent.thinking());
        stream.onEvent(CoachProgressEvent.started("searchExercises"));
        stream.onEvent(CoachProgressEvent.finished("searchExercises", "10 candidates", 19));
        stream.sendAnswer("the answer");

        verify(emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("a client that has gone away stops the stream instead of failing the turn")
    void survivesAClientThatDisconnects() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        SseProgressStream stream = new SseProgressStream(emitter);

        assertThatCode(() -> {
            stream.onEvent(CoachProgressEvent.thinking());
            stream.onEvent(CoachProgressEvent.started("readFacts"));
            stream.sendAnswer("the answer");
        }).doesNotThrowAnyException();

        // One attempt, then nothing: the first failure is how we learn the client is gone, and
        // every write after it would fail the same way.
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("nothing is written after the client's connection has timed out")
    void stopsWritingAfterATimeout() throws IOException {
        RecordingEmitter emitter = new RecordingEmitter();
        SseProgressStream stream = new SseProgressStream(emitter);

        emitter.fireTimeout();
        stream.onEvent(CoachProgressEvent.thinking());

        assertThat(emitter.sent).isZero();
    }

    @Test
    @DisplayName("events from several threads at once are serialised rather than interleaved")
    void serialisesConcurrentEvents() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        SseProgressStream stream = new SseProgressStream(emitter);

        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            for (int i = 0; i < writers; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        stream.onEvent(CoachProgressEvent.started("readFacts"));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(emitter.sent).isEqualTo(writers);
        assertThat(emitter.overlapped).isFalse();
    }

    @Test
    @DisplayName("a failure tells the client the turn failed without handing over the reason")
    void reportsAFailureWithoutItsDetail() {
        RecordingEmitter emitter = new RecordingEmitter();

        new SseProgressStream(emitter).sendFailure();

        assertThat(emitter.sent).isEqualTo(1);
        assertThat(emitter.completed).isTrue();
    }

    @Test
    @DisplayName("completing a stream whose client already left does nothing")
    void completingAfterDisconnectIsSilent() {
        RecordingEmitter emitter = new RecordingEmitter();
        SseProgressStream stream = new SseProgressStream(emitter);

        emitter.fireTimeout();
        stream.complete();

        assertThat(emitter.completed).isFalse();
    }

    /** Counts writes and notices if two ever overlap, which is what the lock has to prevent. */
    private static final class RecordingEmitter extends SseEmitter {

        private Runnable timeoutCallback = () -> {
        };
        private volatile int sent;
        private volatile boolean completed;
        private volatile boolean inSend;
        private volatile boolean overlapped;

        @Override
        public void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        private void fireTimeout() {
            timeoutCallback.run();
        }

        @Override
        public synchronized void send(SseEventBuilder builder) {
            if (inSend) {
                overlapped = true;
            }
            inSend = true;
            sent++;
            inSend = false;
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    @Test
    @DisplayName("a mocked emitter is never completed twice")
    void completesOnce() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseProgressStream stream = new SseProgressStream(emitter);

        stream.complete();
        stream.complete();

        verify(emitter, times(1)).complete();
        verify(emitter, never()).completeWithError(any());
    }
}
