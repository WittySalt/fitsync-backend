package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.analytics.config.CacheConfig;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse.Badge;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse.Evidence;
import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.entity.AnalyticsUserCooksEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import com.fitsync.analytics.entity.AnalyticsWorkoutContributionEntity;
import com.fitsync.analytics.repository.AnalyticsUserCooksRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Badges v2 (me-achievements-spec.md, section 2). The rollups say what the data earns today; the ledger
 * says what was ever earned. A badge the data earns for the first time is written to the ledger with the
 * day it was earned, and from then on the stored day is shown even if the data later changes.
 */
@Service
public class AchievementsService {

    static final String EARNED = "EARNED";
    static final String NEXT = "NEXT";
    static final String LATER = "LATER";
    private static final int NEXT_UP = 2;
    private static final Logger LOG = LoggerFactory.getLogger(AchievementsService.class);

    private final AnalyticsUserDashboardSnapshotRepository snapshotRepository;
    private final AnalyticsUserDailyWorkoutRepository workoutRepository;
    private final AnalyticsUserDailyNutritionRepository nutritionRepository;
    private final AnalyticsUserDailyBodyRepository bodyRepository;
    private final AnalyticsUserGoalProgressRepository goalRepository;
    private final AnalyticsUserWorkoutRecordsRepository recordsRepository;
    private final AnalyticsUserCooksRepository cooksRepository;
    private final AnalyticsWorkoutContributionRepository contributionRepository;
    private final AchievementLedger ledger;
    private final ObjectReader evidenceReader;
    private final ObjectWriter evidenceWriter;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public AchievementsService(AnalyticsUserDashboardSnapshotRepository snapshotRepository,
                               AnalyticsUserDailyWorkoutRepository workoutRepository,
                               AnalyticsUserDailyNutritionRepository nutritionRepository,
                               AnalyticsUserDailyBodyRepository bodyRepository,
                               AnalyticsUserGoalProgressRepository goalRepository,
                               AnalyticsUserWorkoutRecordsRepository recordsRepository,
                               AnalyticsUserCooksRepository cooksRepository,
                               AnalyticsWorkoutContributionRepository contributionRepository,
                               AchievementLedger ledger,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.workoutRepository = workoutRepository;
        this.nutritionRepository = nutritionRepository;
        this.bodyRepository = bodyRepository;
        this.goalRepository = goalRepository;
        this.recordsRepository = recordsRepository;
        this.cooksRepository = cooksRepository;
        this.contributionRepository = contributionRepository;
        this.ledger = ledger;
        this.evidenceReader = objectMapper.readerFor(Evidence.class);
        this.evidenceWriter = objectMapper.writerFor(Evidence.class);
        this.clock = clock;
    }

    @Cacheable(cacheNames = CacheConfig.ACHIEVEMENTS_CACHE,
            key = "#principal.userId() + ':' + #root.target.todayForCacheKey(#principal)")
    public AnalyticsAchievementsResponse getAchievements(CurrentUserPrincipal principal) {
        UUID userId = principal.userId();
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        BadgeCalculator.Result result = BadgeCalculator.compute(inputs(userId, zone, today));
        Map<String, AnalyticsUserAchievementEntity> stored = record(userId, result.badges());

        List<AnalyticsAchievementsResponse.Theme> themes = new ArrayList<>();
        List<Badge> earned = new ArrayList<>();
        List<Badge> candidates = new ArrayList<>();
        for (BadgeCatalogue.ThemeDef theme : BadgeCatalogue.THEMES) {
            List<AnalyticsAchievementsResponse.Path> paths = new ArrayList<>();
            for (BadgeCatalogue.PathDef path : theme.paths()) {
                List<Badge> nodes = new ArrayList<>();
                boolean nextGiven = false;
                for (BadgeCatalogue.BadgeDef def : path.badges()) {
                    AnalyticsUserAchievementEntity row = stored.get(def.code());
                    String state = row != null ? EARNED : nextGiven ? LATER : NEXT;
                    nextGiven = nextGiven || row == null;
                    Badge badge = badge(def, state, row, result.badges().get(def.code()));
                    nodes.add(badge);
                    if (row != null) {
                        earned.add(badge);
                    } else if (NEXT.equals(state) && badge.progress() != null) {
                        candidates.add(badge);
                    }
                }
                paths.add(new AnalyticsAchievementsResponse.Path(path.code(), nodes));
            }
            themes.add(new AnalyticsAchievementsResponse.Theme(theme.code(), theme.name(), paths));
        }

        List<Badge> earlier = BadgeCatalogue.RETIRED.stream()
                .filter(def -> stored.containsKey(def.code()))
                .map(def -> badge(def, EARNED, stored.get(def.code()), null))
                .sorted(Comparator.comparing(Badge::earnedOn))
                .toList();

        return new AnalyticsAchievementsResponse(
                earned.size(),
                BadgeCatalogue.ALL.size(),
                themes,
                latest(earned),
                nextUp(candidates),
                earlier,
                new AnalyticsAchievementsResponse.Pace(result.sessionsPerWeek(), BadgeCalculator.PACE_WEEKS));
    }

    /** Cache-key part, so that entries roll over at the user's midnight. */
    public LocalDate todayForCacheKey(CurrentUserPrincipal principal) {
        return LocalDate.now(clock.withZone(zoneOf(principal.userId())));
    }

    private BadgeCalculator.Inputs inputs(UUID userId, ZoneId zone, LocalDate today) {
        AnalyticsUserWorkoutRecordsEntity records = recordsRepository.findById(userId).orElse(null);
        AnalyticsUserCooksEntity cooks = cooksRepository.findById(userId).orElse(null);
        Map<String, BigDecimal> targets = activeTargets(userId);
        BigDecimal sessions = targets.get(GoalProgressEvaluator.WORKOUT_FREQUENCY);
        return new BadgeCalculator.Inputs(
                today,
                zone,
                workoutRepository.findByUserIdOrderByActivityDateAsc(userId),
                nutritionRepository.findByUserIdOrderByActivityDateAsc(userId),
                sessions == null ? null : sessions.setScale(0, RoundingMode.CEILING).intValue(),
                targets.get(GoalProgressEvaluator.PROTEIN),
                records == null || records.getPersonalRecordCount() == null ? 0 : records.getPersonalRecordCount(),
                records == null ? null : records.getFirstRecordAt(),
                records == null ? null : records.getLastRecordAt(),
                cooks == null || cooks.getCooks() == null ? 0 : cooks.getCooks(),
                cooks == null ? null : cooks.getFirstCookAt(),
                cooks == null ? null : cooks.getLastCookAt(),
                bodyRepository.findTopByUserIdAndWeightKgIsNotNullOrderByMetricDateAsc(userId)
                        .map(AnalyticsUserDailyBodyEntity::getMetricDate)
                        .orElse(null));
    }

    private Map<String, BigDecimal> activeTargets(UUID userId) {
        Map<String, BigDecimal> targets = new HashMap<>();
        for (AnalyticsUserGoalProgressEntity goal : goalRepository
                .findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(userId,
                        GoalExpiryPolicy.ACTIVE_STATUS,
                        Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY, GoalProgressEvaluator.PROTEIN))) {
            if (goal.getTargetValue() != null && goal.getTargetValue().signum() > 0) {
                targets.putIfAbsent(goal.getGoalType(), goal.getTargetValue());
            }
        }
        return targets;
    }

    /**
     * Writes every badge the data earns that the ledger does not hold yet, retired ones included, and
     * answers the ledger as it then stands.
     */
    private Map<String, AnalyticsUserAchievementEntity> record(UUID userId,
                                                               Map<String, BadgeCalculator.Computed> computed) {
        Map<String, AnalyticsUserAchievementEntity> stored = new HashMap<>();
        for (AnalyticsUserAchievementEntity row : ledger.stored(userId)) {
            stored.put(row.getCode(), row);
        }
        List<AnalyticsUserAchievementEntity> fresh = new ArrayList<>();
        Instant now = clock.instant();
        for (BadgeCalculator.Computed badge : computed.values()) {
            if (!badge.earned() || stored.containsKey(badge.code())) {
                continue;
            }
            AnalyticsUserAchievementEntity row = new AnalyticsUserAchievementEntity();
            row.setUserId(userId);
            row.setCode(badge.code());
            row.setEarnedOn(badge.earnedOn());
            row.setEvidenceJson(writeEvidence(withSession(userId, badge)));
            row.setCreated(now);
            fresh.add(row);
            stored.put(badge.code(), row);
        }
        if (!fresh.isEmpty()) {
            ledger.add(fresh);
            // Read back what is stored now, so a badge another read stored first shows its own date.
            Map<String, AnalyticsUserAchievementEntity> reread = new HashMap<>();
            for (AnalyticsUserAchievementEntity row : ledger.stored(userId)) {
                reread.put(row.getCode(), row);
            }
            for (AnalyticsUserAchievementEntity row : fresh) {
                reread.putIfAbsent(row.getCode(), row);
            }
            return reread;
        }
        return stored;
    }

    /** The session that crossed a session-count line, when the contributions still hold it. */
    private Evidence withSession(UUID userId, BadgeCalculator.Computed badge) {
        Evidence evidence = badge.evidence();
        BadgeCalculator.SessionRef ref = badge.session();
        if (ref == null || evidence == null) {
            return evidence;
        }
        List<AnalyticsWorkoutContributionEntity> sessions = new ArrayList<>(
                contributionRepository.findByUserIdAndActivityDate(userId, ref.date()));
        sessions.sort(Comparator.comparing(AnalyticsWorkoutContributionEntity::getLastAppliedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int index = ref.indexInDay() - 1;
        if (index < 0 || index >= sessions.size()) {
            return evidence;
        }
        return new Evidence(evidence.weekStart(), evidence.monthStart(), sessions.get(index).getSessionId(),
                evidence.recordId(), evidence.weekDays());
    }

    private Badge badge(BadgeCatalogue.BadgeDef def, String state, AnalyticsUserAchievementEntity row,
                        BadgeCalculator.Computed computed) {
        return new Badge(
                def.code(),
                def.theme(),
                def.name(),
                def.how(),
                state,
                row == null ? null : row.getEarnedOn(),
                computed == null ? null : computed.progress(),
                row == null ? null : readEvidence(row.getEvidenceJson()));
    }

    /** The most recently earned badge; on the same day, the one further along the catalogue. */
    private static Badge latest(List<Badge> earned) {
        Badge latest = null;
        for (Badge badge : earned) {
            if (latest == null || !badge.earnedOn().isBefore(latest.earnedOn())) {
                latest = badge;
            }
        }
        return latest;
    }

    /** The closest next badges by share done; ties go to the theme that is easiest to act on. */
    private static List<Badge> nextUp(List<Badge> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(AchievementsService::share).reversed()
                        .thenComparing(badge -> BadgeCatalogue.TIE_ORDER.indexOf(badge.theme()))
                        .thenComparing(badge -> catalogueIndex(badge.code())))
                .limit(NEXT_UP)
                .toList();
    }

    private static BigDecimal share(Badge badge) {
        BigDecimal target = badge.progress().target();
        if (target == null || target.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal share = badge.progress().current().divide(target, 4, RoundingMode.HALF_UP);
        return share.min(BigDecimal.ONE);
    }

    private static int catalogueIndex(String code) {
        for (int i = 0; i < BadgeCatalogue.ALL.size(); i++) {
            if (BadgeCatalogue.ALL.get(i).code().equals(code)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String writeEvidence(Evidence evidence) {
        if (evidence == null) {
            return null;
        }
        try {
            return evidenceWriter.writeValueAsString(evidence);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write badge evidence", ex);
        }
    }

    private Evidence readEvidence(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return evidenceReader.readValue(json);
        } catch (JsonProcessingException ex) {
            // A stored badge stays earned even when its evidence can no longer be read.
            LOG.warn("event=badge_evidence_unreadable");
            return null;
        }
    }

    private ZoneId zoneOf(UUID userId) {
        String timezone = snapshotRepository.findById(userId)
                .map(AnalyticsUserDashboardSnapshotEntity::getTimezone)
                .orElse(null);
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }
}
