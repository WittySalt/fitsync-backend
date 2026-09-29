package com.fitsync.recommendation.coach.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Every number the admin coach-quality page renders, for one date range in one timezone.
 *
 * <p>Counts, rule codes, tool names, model names and durations only. Nothing here can be traced
 * back to a user, a thread or a turn's text; the privacy rule for this page is enforced by what
 * {@link com.fitsync.recommendation.coach.service.CoachQualityService} selects out of the
 * repositories, not by filtering a response afterwards.
 */
@Schema(description = "Coach answer quality for a date range: pass/reject/refuse counts, latency, "
        + "tool and reading telemetry, and a daily trend.")
public record CoachQualityResponse(
        String from,
        String to,
        String timezone,
        CoachQualityAnswersView answers,
        CoachQualityLatencyView latency,
        List<CoachQualityTierCountView> tiers,
        List<CoachQualityModelCountView> models,
        List<CoachQualityToolView> tools,
        List<CoachQualityReadingCountView> readings,
        List<CoachQualityDailyView> daily) {

    public CoachQualityResponse {
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
        models = models == null ? List.of() : List.copyOf(models);
        tools = tools == null ? List.of() : List.copyOf(tools);
        readings = readings == null ? List.of() : List.copyOf(readings);
        daily = daily == null ? List.of() : List.copyOf(daily);
    }

    @Override
    public List<CoachQualityTierCountView> tiers() {
        return List.copyOf(tiers);
    }

    @Override
    public List<CoachQualityModelCountView> models() {
        return List.copyOf(models);
    }

    @Override
    public List<CoachQualityToolView> tools() {
        return List.copyOf(tools);
    }

    @Override
    public List<CoachQualityReadingCountView> readings() {
        return List.copyOf(readings);
    }

    @Override
    public List<CoachQualityDailyView> daily() {
        return List.copyOf(daily);
    }
}
