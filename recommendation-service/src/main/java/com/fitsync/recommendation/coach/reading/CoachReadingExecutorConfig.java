package com.fitsync.recommendation.coach.reading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs page-reading generation off the request thread, the same way {@code WeeklyNarrativeService}
 * runs the weekly narrative: one daemon thread, because this is low-frequency background work with
 * nothing in common with the coach's own tool pool ({@code CoachExecutorConfig}), which exists for
 * the very different job of fanning out a turn's catalogue reads. Named as a bean, rather than a
 * private field on the service, so a test can replace it with a same-thread executor and assert on
 * the result of a generation without a race against a background thread.
 */
@Configuration
public class CoachReadingExecutorConfig {

    @Bean(name = "coachReadingExecutor", destroyMethod = "shutdown")
    public ExecutorService coachReadingExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coach-reading-generator");
            thread.setDaemon(true);
            return thread;
        });
    }
}
