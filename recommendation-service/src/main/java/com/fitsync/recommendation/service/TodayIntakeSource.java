package com.fitsync.recommendation.service;

import java.time.LocalDate;
import java.time.ZoneId;

import com.fitsync.recommendation.domain.TodayIntake;

/**
 * Reads one local day's intake from the service that owns the diary.
 *
 * <p>A port, so the rules and the resolver can use the diary without depending on the coach's HTTP
 * clients (the projection never imports the coach package). Implementations never throw: a read
 * that fails, or a missing token, is {@link TodayIntake#unknown()}.
 */
public interface TodayIntakeSource {

    TodayIntake read(String bearerToken, LocalDate date, ZoneId zone);
}
