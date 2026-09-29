package com.fitsync.recommendation.coach.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fitsync.recommendation.coach.dto.CoachQualityAnswersView;
import com.fitsync.recommendation.coach.dto.CoachQualityDailyView;
import com.fitsync.recommendation.coach.dto.CoachQualityLatencyView;
import com.fitsync.recommendation.coach.dto.CoachQualityModelCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityReadingCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityResponse;
import com.fitsync.recommendation.coach.dto.CoachQualityRuleCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityTierCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityToolView;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.reading.CoachPageReadingQualityRow;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnQualityRow;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.ToolInvocationQualityRow;
import com.fitsync.recommendation.coach.repository.ToolInvocationRepository;
import com.fitsync.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the numbers behind the admin coach-quality page from raw turn, tool and reading rows.
 *
 * <p>Every read goes through a constructor projection that never selects a turn's {@code content},
 * a reading's {@code headline}/{@code body}, or a tool's {@code argsJson}/{@code resultSummary} — see
 * the {@code *QualityRow} projections each repository query returns. That is what makes the privacy
 * rule for this page a property of the query rather than something this class has to remember to
 * uphold when it builds the response.
 */
@Service
public class CoachQualityService {

    /** The default range when {@code to} is given but {@code from} is not: the day itself plus six before it. */
    private static final int DEFAULT_WINDOW_DAYS = 7;
    /** The widest range this report will scan, so an open-ended query cannot become an unbounded one. */
    private static final int MAX_RANGE_DAYS = 92;
    private static final String DEFAULT_TIMEZONE = "UTC";
    /**
     * One message for every way the request can be malformed. Never built from the caller's own
     * {@code from}/{@code to}/{@code timezone} values, so a client cannot use this endpoint to get
     * its own input echoed back.
     */
    private static final String INVALID_PARAMS_MESSAGE =
            "The date range or timezone for the coach quality report is invalid.";

    private final CoachTurnRepository turnRepository;
    private final ToolInvocationRepository toolInvocationRepository;
    private final CoachPageReadingRepository readingRepository;
    private final Clock clock;

    public CoachQualityService(CoachTurnRepository turnRepository,
                               ToolInvocationRepository toolInvocationRepository,
                               CoachPageReadingRepository readingRepository,
                               Clock clock) {
        this.turnRepository = turnRepository;
        this.toolInvocationRepository = toolInvocationRepository;
        this.readingRepository = readingRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CoachQualityResponse quality(LocalDate from, LocalDate to, String timezone) {
        ZoneId zone = resolveZone(timezone);
        LocalDate resolvedTo = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        validateRange(resolvedFrom, resolvedTo);

        // The end bound is exclusive, one instant past midnight of the day after `to`, so a turn
        // recorded anywhere within the last calendar day of the range is still included.
        Instant start = resolvedFrom.atStartOfDay(zone).toInstant();
        Instant end = resolvedTo.plusDays(1).atStartOfDay(zone).toInstant();

        List<CoachTurnQualityRow> turns = turnRepository.findQualityRows(CoachTurnRole.ASSISTANT, start, end);
        List<ToolInvocationQualityRow> toolRows = toolInvocationRepository.findQualityRows(start, end);
        List<CoachPageReadingQualityRow> readingRows = readingRepository.findQualityRows(start, end);

        return new CoachQualityResponse(
                resolvedFrom.toString(),
                resolvedTo.toString(),
                zone.getId(),
                buildAnswers(turns),
                buildLatency(turns),
                buildTiers(turns),
                buildModels(turns),
                buildTools(toolRows),
                buildReadings(readingRows),
                buildDaily(resolvedFrom, resolvedTo, zone, turns));
    }

    private ZoneId resolveZone(String timezone) {
        String candidate = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
        try {
            return ZoneId.of(candidate);
        } catch (DateTimeException ex) {
            throw invalidParams();
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw invalidParams();
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw invalidParams();
        }
    }

    private static ApiException invalidParams() {
        return new ApiException(HttpStatus.BAD_REQUEST, "COACH_QUALITY_INVALID_PARAMS", INVALID_PARAMS_MESSAGE);
    }

    // ---------------------------------------------------------------------------------------
    // Classification of one turn's guardrailVerdict, shared by the summary and the daily trend.

    private enum Classification {
        PASSED, REJECTED, REFUSED, MODEL_UNAVAILABLE, OTHER
    }

    private record Classified(Classification classification, String rule) {
    }

    /**
     * Reads the fixed shape a verdict is written in — see {@link CoachOutcome} — without repeating
     * its prefix constants as string literals here.
     */
    private static Classified classify(String verdict) {
        if (verdict == null) {
            return new Classified(Classification.OTHER, null);
        }
        if (verdict.equals(CoachOutcome.VERDICT_PASSED)) {
            return new Classified(Classification.PASSED, null);
        }
        if (verdict.startsWith(CoachOutcome.VERDICT_ANSWER_REJECTED_PREFIX)) {
            String remainder = verdict.substring(CoachOutcome.VERDICT_ANSWER_REJECTED_PREFIX.length());
            int colon = remainder.indexOf(':');
            String rule = colon >= 0 ? remainder.substring(0, colon) : remainder;
            return new Classified(Classification.REJECTED, rule);
        }
        if (verdict.startsWith(CoachOutcome.VERDICT_REFUSED_PREFIX)) {
            String rule = verdict.substring(CoachOutcome.VERDICT_REFUSED_PREFIX.length());
            return new Classified(Classification.REFUSED, rule);
        }
        if (verdict.equals(CoachOutcome.VERDICT_MODEL_OFF)) {
            return new Classified(Classification.MODEL_UNAVAILABLE, null);
        }
        return new Classified(Classification.OTHER, null);
    }

    private CoachQualityAnswersView buildAnswers(List<CoachTurnQualityRow> turns) {
        int passed = 0;
        int rejected = 0;
        int refused = 0;
        int modelUnavailable = 0;
        int other = 0;
        Map<String, Integer> rejectedByRule = new LinkedHashMap<>();
        Map<String, Integer> refusedByRule = new LinkedHashMap<>();

        for (CoachTurnQualityRow turn : turns) {
            Classified classified = classify(turn.guardrailVerdict());
            switch (classified.classification()) {
                case PASSED -> passed++;
                case REJECTED -> {
                    rejected++;
                    rejectedByRule.merge(classified.rule(), 1, Integer::sum);
                }
                case REFUSED -> {
                    refused++;
                    refusedByRule.merge(classified.rule(), 1, Integer::sum);
                }
                case MODEL_UNAVAILABLE -> modelUnavailable++;
                case OTHER -> other++;
            }
        }

        return new CoachQualityAnswersView(
                turns.size(), passed, rejected, refused, modelUnavailable, other,
                toRuleCounts(rejectedByRule), toRuleCounts(refusedByRule));
    }

    private static List<CoachQualityRuleCountView> toRuleCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(entry -> new CoachQualityRuleCountView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(CoachQualityRuleCountView::count).reversed()
                        .thenComparing(CoachQualityRuleCountView::rule))
                .toList();
    }

    private static CoachQualityLatencyView buildLatency(List<CoachTurnQualityRow> turns) {
        List<Integer> values = turns.stream()
                .filter(turn -> classify(turn.guardrailVerdict()).classification() == Classification.PASSED)
                .map(CoachTurnQualityRow::latencyMs)
                .filter(latency -> latency != null && latency > 0)
                .sorted()
                .toList();
        return new CoachQualityLatencyView(values.size(), nearestRank(values, 50), nearestRank(values, 95));
    }

    /**
     * Nearest-rank percentile over an already-sorted list: {@code rank = ceil(p/100 * n)}, one-based,
     * clamped into range. Returns 0 for an empty list rather than throwing, since "no passed answers
     * yet today" is an ordinary state for this report, not an error.
     */
    private static int nearestRank(List<Integer> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(percentile / 100.0 * sortedValues.size());
        rank = Math.max(1, Math.min(rank, sortedValues.size()));
        return sortedValues.get(rank - 1);
    }

    private static List<CoachQualityTierCountView> buildTiers(List<CoachTurnQualityRow> turns) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (CoachTurnQualityRow turn : turns) {
            if (turn.tier() != null) {
                counts.merge(turn.tier(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new CoachQualityTierCountView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(CoachQualityTierCountView::count).reversed()
                        .thenComparingInt(CoachQualityTierCountView::tier))
                .toList();
    }

    private static List<CoachQualityModelCountView> buildModels(List<CoachTurnQualityRow> turns) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CoachTurnQualityRow turn : turns) {
            if (turn.model() != null) {
                counts.merge(turn.model(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new CoachQualityModelCountView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(CoachQualityModelCountView::count).reversed()
                        .thenComparing(CoachQualityModelCountView::model))
                .toList();
    }

    private static List<CoachQualityToolView> buildTools(List<ToolInvocationQualityRow> rows) {
        Map<String, List<ToolInvocationQualityRow>> byTool = new LinkedHashMap<>();
        for (ToolInvocationQualityRow row : rows) {
            byTool.computeIfAbsent(row.tool(), key -> new ArrayList<>()).add(row);
        }
        return byTool.entrySet().stream()
                .map(entry -> {
                    List<ToolInvocationQualityRow> calls = entry.getValue();
                    int failed = (int) calls.stream().filter(call -> !call.ok()).count();
                    List<Integer> latencies = calls.stream()
                            .map(ToolInvocationQualityRow::latencyMs)
                            .sorted()
                            .toList();
                    return new CoachQualityToolView(entry.getKey(), calls.size(), failed, nearestRank(latencies, 50));
                })
                .sorted(Comparator.comparingInt(CoachQualityToolView::calls).reversed()
                        .thenComparing(CoachQualityToolView::tool))
                .toList();
    }

    private static List<CoachQualityReadingCountView> buildReadings(List<CoachPageReadingQualityRow> rows) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        for (CoachPageReadingQualityRow row : rows) {
            String surface = row.surface() == null ? "" : row.surface().name();
            String source = row.source() == null ? "" : row.source();
            String outcome = row.outcome() == null ? "" : row.outcome().name();
            counts.merge(List.of(surface, source, outcome), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new CoachQualityReadingCountView(
                        entry.getKey().get(0), entry.getKey().get(1), entry.getKey().get(2), entry.getValue()))
                .sorted(Comparator.comparingInt(CoachQualityReadingCountView::count).reversed()
                        .thenComparing(CoachQualityReadingCountView::surface)
                        .thenComparing(CoachQualityReadingCountView::source)
                        .thenComparing(CoachQualityReadingCountView::outcome))
                .toList();
    }

    private static List<CoachQualityDailyView> buildDaily(LocalDate from, LocalDate to, ZoneId zone,
                                                           List<CoachTurnQualityRow> turns) {
        // answers, passed, rejected, refused, modelUnavailable — one bucket per calendar date in the
        // range, seeded up front so a day with no turns still appears with zeros.
        Map<LocalDate, int[]> byDay = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            byDay.put(date, new int[5]);
        }
        for (CoachTurnQualityRow turn : turns) {
            LocalDate date = turn.dateTimeCreated().atZone(zone).toLocalDate();
            int[] bucket = byDay.get(date);
            if (bucket == null) {
                // The query window already excludes anything outside [from, to] in this zone; kept
                // as a guard rather than an assumption.
                continue;
            }
            bucket[0]++;
            switch (classify(turn.guardrailVerdict()).classification()) {
                case PASSED -> bucket[1]++;
                case REJECTED -> bucket[2]++;
                case REFUSED -> bucket[3]++;
                case MODEL_UNAVAILABLE -> bucket[4]++;
                case OTHER -> {
                }
            }
        }
        return byDay.entrySet().stream()
                .map(entry -> new CoachQualityDailyView(
                        entry.getKey().toString(),
                        entry.getValue()[0], entry.getValue()[1], entry.getValue()[2],
                        entry.getValue()[3], entry.getValue()[4]))
                .toList();
    }
}
