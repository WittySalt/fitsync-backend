package com.fitsync.recommendation.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachQualityDailyView;
import com.fitsync.recommendation.coach.dto.CoachQualityReadingCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityResponse;
import com.fitsync.recommendation.coach.dto.CoachQualityRuleCountView;
import com.fitsync.recommendation.coach.dto.CoachQualityToolView;
import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.entity.ToolInvocationEntity;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.reading.CoachPageReadingEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.reading.ReadingOutcome;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.ToolInvocationRepository;
import com.fitsync.recommendation.coach.service.CoachQualityService;
import com.fitsync.web.error.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin coach-quality aggregate: classification of the guardrail verdict, latency percentiles,
 * grouping by tier/model/tool/reading, the daily trend, and the window boundaries the report is
 * scoped to.
 *
 * <p>Every seeded row is written through its repository, exactly as production code would, and the
 * timestamps under test are then overwritten with a native update — {@code date_time_created} is a
 * Spring Data auditing column, set on insert, so this is the only way a test can put a row at an
 * exact instant outside "now".
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachQualityServiceTests {

    @Autowired
    private CoachQualityService service;

    @Autowired
    private CoachThreadRepository threadRepository;

    @Autowired
    private CoachTurnRepository turnRepository;

    @Autowired
    private ToolInvocationRepository toolInvocationRepository;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID threadId;

    @BeforeEach
    void createThread() {
        CoachThreadEntity thread = new CoachThreadEntity();
        thread.setThreadId(UUID.randomUUID());
        thread.setUserId(UUID.randomUUID());
        thread.setArchived(false);
        thread.setLastActiveAt(Instant.parse("2026-09-24T00:00:00Z"));
        threadRepository.save(thread);
        threadId = thread.getThreadId();
    }

    @Test
    @DisplayName("a rejected verdict is classified with the detail stripped, a refused verdict keeps its whole rule")
    void classifiesAndExtractsRulesWithoutTheDetail() {
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "claude-haiku-4-5-20251001", 2100,
                Instant.parse("2026-09-25T10:00:00Z"));
        seedTurn("ANSWER_REJECTED:NUMBER_NOT_IN_RECORDS:1735", 2, "claude-haiku-4-5-20251001", 1800,
                Instant.parse("2026-09-25T11:00:00Z"));
        seedTurn("ANSWER_REJECTED:WEEK_NOT_NAMED", 2, "claude-haiku-4-5-20251001", 1900,
                Instant.parse("2026-09-25T12:00:00Z"));
        seedTurn("REFUSED_CALORIE_FLOOR", 0, null, null, Instant.parse("2026-09-25T13:00:00Z"));
        seedTurn(CoachOutcome.VERDICT_MODEL_OFF, 3, null, null, Instant.parse("2026-09-25T14:00:00Z"));
        seedTurn(null, null, null, null, Instant.parse("2026-09-25T15:00:00Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.answers().total()).isEqualTo(6);
        assertThat(response.answers().passed()).isEqualTo(1);
        assertThat(response.answers().rejected()).isEqualTo(2);
        assertThat(response.answers().refused()).isEqualTo(1);
        assertThat(response.answers().modelUnavailable()).isEqualTo(1);
        assertThat(response.answers().other()).isEqualTo(1);
        assertThat(response.answers().rejectedByRule())
                .extracting(CoachQualityRuleCountView::rule)
                .containsExactlyInAnyOrder("NUMBER_NOT_IN_RECORDS", "WEEK_NOT_NAMED");
        assertThat(response.answers().refusedByRule())
                .containsExactly(new CoachQualityRuleCountView("CALORIE_FLOOR", 1));
    }

    @Test
    @DisplayName("a row just before the window and one at or after its exclusive end are both left out")
    void excludesRowsJustOutsideTheWindow() {
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-09-23T23:59:59Z")); // before
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-09-24T00:00:00Z")); // start, included
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-09-30T23:59:59Z")); // last included instant
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-10-01T00:00:00Z")); // exclusive end

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.answers().total()).isEqualTo(2);
    }

    @Test
    @DisplayName("the timezone shifts a turn recorded near midnight UTC into the following local day")
    void timezoneShiftsATurnIntoTheFollowingLocalDay() {
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-09-29T17:30:00Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), "Asia/Singapore");

        assertThat(response.daily())
                .filteredOn(day -> day.date().equals("2026-09-30"))
                .singleElement()
                .extracting(CoachQualityDailyView::answers)
                .isEqualTo(1);
        assertThat(response.daily())
                .filteredOn(day -> day.date().equals("2026-09-29"))
                .singleElement()
                .extracting(CoachQualityDailyView::answers)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("daily has one zero entry for every date in the range that recorded nothing")
    void dailyHasZeroEntriesForEmptyDays() {
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100, Instant.parse("2026-09-24T10:00:00Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.daily()).hasSize(7);
        assertThat(response.daily()).extracting(CoachQualityDailyView::date)
                .containsExactly("2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27",
                        "2026-09-28", "2026-09-29", "2026-09-30");
        assertThat(response.daily().get(0).answers()).isEqualTo(1);
        assertThat(response.daily().subList(1, 7))
                .allSatisfy(day -> assertThat(day.answers()).isZero());
    }

    @Test
    @DisplayName("median and p95 use the nearest-rank method over passed answers only")
    void nearestRankMedianAndP95() {
        int[] latencies = {100, 200, 300, 400, 1000};
        Instant at = Instant.parse("2026-09-25T00:00:00Z");
        for (int latency : latencies) {
            seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", latency, at);
        }
        // A zero and a null latency on otherwise-passed turns must not count towards the sample.
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 0, at);
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", null, at);

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.latency().count()).isEqualTo(5);
        assertThat(response.latency().medianMs()).isEqualTo(300);
        assertThat(response.latency().p95Ms()).isEqualTo(1000);
    }

    @Test
    @DisplayName("an empty sample reports a zero median and p95 rather than failing")
    void emptySampleReportsZero() {
        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.latency().count()).isZero();
        assertThat(response.latency().medianMs()).isZero();
        assertThat(response.latency().p95Ms()).isZero();
    }

    @Test
    @DisplayName("tools are grouped by name with call counts, failures and a nearest-rank median")
    void toolsAggregateAcrossCalls() {
        CoachTurnEntity turn = seedTurn(CoachOutcome.VERDICT_PASSED, 2, "m", 100,
                Instant.parse("2026-09-25T00:00:00Z"));
        seedTool(turn.getTurnId(), "readFacts", 10, true, Instant.parse("2026-09-25T00:00:01Z"));
        seedTool(turn.getTurnId(), "readFacts", 20, true, Instant.parse("2026-09-25T00:00:02Z"));
        seedTool(turn.getTurnId(), "readFacts", 30, false, Instant.parse("2026-09-25T00:00:03Z"));
        seedTool(turn.getTurnId(), "searchExercises", 5, true, Instant.parse("2026-09-25T00:00:04Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.tools()).containsExactly(
                new CoachQualityToolView("readFacts", 3, 1, 20),
                new CoachQualityToolView("searchExercises", 1, 0, 5));
    }

    @Test
    @DisplayName("readings are grouped by surface, source and outcome")
    void readingsAggregateBySurfaceSourceOutcome() {
        seedReading(ReadingSurface.WORKOUT, "MODEL", ReadingOutcome.ACCEPTED, Instant.parse("2026-09-25T00:00:00Z"));
        seedReading(ReadingSurface.WORKOUT, "MODEL", ReadingOutcome.ACCEPTED, Instant.parse("2026-09-26T00:00:00Z"));
        seedReading(ReadingSurface.WORKOUT, "RULES", ReadingOutcome.ACCEPTED, Instant.parse("2026-09-27T00:00:00Z"));
        seedReading(ReadingSurface.NUTRITION, "MODEL", ReadingOutcome.ACCEPTED, Instant.parse("2026-09-27T01:00:00Z"));
        seedReading(ReadingSurface.PROGRESS, "RULES", ReadingOutcome.REJECTED, Instant.parse("2026-09-27T02:00:00Z"));
        seedReading(ReadingSurface.PROGRESS, "RULES", ReadingOutcome.REJECTED, Instant.parse("2026-09-27T03:00:00Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.readings()).containsExactly(
                new CoachQualityReadingCountView("PROGRESS", "RULES", "REJECTED", 2),
                new CoachQualityReadingCountView("WORKOUT", "MODEL", "ACCEPTED", 2),
                new CoachQualityReadingCountView("NUTRITION", "MODEL", "ACCEPTED", 1),
                new CoachQualityReadingCountView("WORKOUT", "RULES", "ACCEPTED", 1));
    }

    @Test
    @DisplayName("tiers and models leave out turns where the value is null")
    void tiersAndModelsExcludeNulls() {
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "model-a", 100, Instant.parse("2026-09-25T00:00:00Z"));
        seedTurn(CoachOutcome.VERDICT_PASSED, 2, "model-a", 100, Instant.parse("2026-09-26T00:00:00Z"));
        seedTurn("REFUSED_CALORIE_FLOOR", null, null, null, Instant.parse("2026-09-27T00:00:00Z"));

        CoachQualityResponse response = service.quality(
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"), null);

        assertThat(response.tiers()).containsExactly(
                new com.fitsync.recommendation.coach.dto.CoachQualityTierCountView(2, 2));
        assertThat(response.models()).containsExactly(
                new com.fitsync.recommendation.coach.dto.CoachQualityModelCountView("model-a", 2));
    }

    @Test
    @DisplayName("defaults are the last 7 days in UTC when nothing is given")
    void defaultsToLastSevenDaysInUtc() {
        CoachQualityResponse response = service.quality(null, null, null);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(response.to()).isEqualTo(today.toString());
        assertThat(response.from()).isEqualTo(today.minusDays(6).toString());
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.daily()).hasSize(7);
    }

    @Test
    @DisplayName("from after to is rejected")
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> service.quality(
                LocalDate.parse("2026-09-30"), LocalDate.parse("2026-09-24"), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a range wider than 92 days is rejected")
    void rangeOver92DaysIsRejected() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = from.plusDays(92); // 93 calendar days inclusive

        assertThatThrownBy(() -> service.quality(from, to, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a range of exactly 92 days is accepted")
    void rangeOfExactly92DaysIsAccepted() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = from.plusDays(91); // 92 calendar days inclusive

        CoachQualityResponse response = service.quality(from, to, null);

        assertThat(response.daily()).hasSize(92);
    }

    @Test
    @DisplayName("an unrecognised timezone id is rejected")
    void unknownTimezoneIsRejected() {
        assertThatThrownBy(() -> service.quality(null, null, "Mars/Base"))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------------------------------

    private CoachTurnEntity seedTurn(String guardrailVerdict, Integer tier, String model, Integer latencyMs,
                                     Instant createdAt) {
        CoachTurnEntity turn = new CoachTurnEntity();
        turn.setTurnId(UUID.randomUUID());
        turn.setThreadId(threadId);
        turn.setSeq(turnRepository.findMaxSeq(threadId) + 1);
        turn.setRole(CoachTurnRole.ASSISTANT);
        turn.setContent("seeded answer text for a quality test, never meant to leave this test");
        turn.setTier(tier);
        turn.setModel(model);
        turn.setLatencyMs(latencyMs);
        turn.setGuardrailVerdict(guardrailVerdict);
        turnRepository.saveAndFlush(turn);
        overwriteTimestamp("coach_turn", "turn_id", turn.getTurnId(), "date_time_created", createdAt);
        return turn;
    }

    private void seedTool(UUID turnId, String tool, int latencyMs, boolean ok, Instant createdAt) {
        ToolInvocationEntity invocation = new ToolInvocationEntity();
        invocation.setInvocationId(UUID.randomUUID());
        invocation.setTurnId(turnId);
        invocation.setSeq(toolInvocationRepository.findByTurnIdOrderBySeqAsc(turnId).size() + 1);
        invocation.setTool(tool);
        invocation.setLatencyMs(latencyMs);
        invocation.setOk(ok);
        toolInvocationRepository.saveAndFlush(invocation);
        overwriteTimestamp("tool_invocation", "invocation_id", invocation.getInvocationId(),
                "date_time_created", createdAt);
    }

    private void seedReading(ReadingSurface surface, String source, ReadingOutcome outcome, Instant updatedAt) {
        CoachPageReadingEntity reading = new CoachPageReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setUserId(UUID.randomUUID());
        reading.setSurface(surface);
        reading.setFactsHash("a".repeat(64));
        reading.setOutcome(outcome);
        reading.setSource(source);
        reading.setStartedAt(updatedAt);
        readingRepository.saveAndFlush(reading);
        overwriteTimestamp("coach_page_reading", "id", reading.getId(), "date_time_updated", updatedAt);
    }

    /**
     * {@code date_time_created}/{@code date_time_updated} are Spring Data auditing columns, stamped
     * with "now" on save. A native update after the fact is the only way to put a seeded row at an
     * exact instant for a window-boundary test; {@link EntityManager#clear()} afterwards forces the
     * next query to read the overwritten value rather than the cached one.
     */
    private void overwriteTimestamp(String table, String idColumn, UUID id, String timestampColumn, Instant at) {
        entityManager.createNativeQuery(
                        "update recommendation." + table + " set " + timestampColumn + " = :at where "
                                + idColumn + " = :id")
                .setParameter("at", Timestamp.from(at))
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
    }
}
