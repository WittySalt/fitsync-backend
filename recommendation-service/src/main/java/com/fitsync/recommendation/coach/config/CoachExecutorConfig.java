package com.fitsync.recommendation.coach.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The coach's own thread pool, which is the bulkhead ADR-001 argued for.
 *
 * <p>The service runs two workloads with nothing in common. The Kafka consumers are high frequency,
 * short lived and must not block. The coach is low frequency and waits seconds at a time on an
 * external model. Sharing a pool means a slow model can starve the projection, which is the
 * failure the invariant "consumers never call a model" exists to prevent; enforcing that invariant
 * in the package structure while letting both contend for the same threads would only move the
 * problem.
 *
 * <p>Small and bounded on purpose. Two threads are enough to fan out the independent reads a
 * planned turn makes, and a short queue with a caller-runs policy means a burst degrades into
 * slower turns rather than an unbounded backlog.
 */
@Configuration
public class CoachExecutorConfig {

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 16;
    private static final long KEEP_ALIVE_SECONDS = 30L;
    private static final int TURN_THREADS = 4;
    private static final int TURN_QUEUE_CAPACITY = 32;

    /**
     * Runs a whole streamed turn, so the request thread can return the emitter and let the servlet
     * container release it.
     *
     * <p>Separate from the tool pool rather than shared with it. A turn waits on its own planned
     * tools, so running both on one two-thread pool would have turns holding the threads their
     * tools need; the caller-runs policy stops that from deadlocking outright but converts the
     * concurrency the planned path exists for back into a serial gather.
     */
    @Bean(name = "coachTurnExecutor", destroyMethod = "shutdown")
    public ExecutorService coachTurnExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                TURN_THREADS, TURN_THREADS,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(TURN_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "coach-turn-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "coachToolExecutor", destroyMethod = "shutdown")
    public ExecutorService coachToolExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                THREADS, THREADS,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "coach-tool-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                // Under load the calling thread does the work itself: the turn gets slower, the
                // queue does not grow without limit, and nothing is silently dropped.
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
