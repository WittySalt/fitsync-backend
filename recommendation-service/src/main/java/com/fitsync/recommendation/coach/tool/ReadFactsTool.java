package com.fitsync.recommendation.coach.tool;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.client.DiaryTodayIntakeSource;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.client.UserProfileClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.reading.ProgressFactsBuilder;
import com.fitsync.recommendation.coach.reading.ProgressInsight;
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.springframework.stereotype.Component;

/**
 * The user's computed facts: the last full week's training, today's nutrition, goal pace, the streak,
 * the last personal record and the weekly food-group split.
 *
 * <p>This is the tool that makes the coach different from a chatbot. Every number in it was worked
 * out by the rule engine from the analytics read model before the model saw the question, so the
 * model phrases rather than calculates. It is also the tool whose absence defines the envelope: if
 * something is not in here and not in a catalogue, the coach does not know it, and saying so is the
 * correct answer.
 *
 * <p>The computed facts are read in-process, because they belong to this service. The training
 * counts among them describe the last full week, which is the week the weekly recap covered. A
 * question about "my week" is about the current one, so the tool also asks workout-service for the
 * current week so far and hands both over, each under its own name. When workout-service cannot be
 * reached the current week is left out and the rest is returned as it is.
 */
@Component
public class ReadFactsTool implements CoachTool {

    private final RecommendationContextRepository contextRepository;
    private final CoachFacts coachFacts;
    private final ObjectMapper objectMapper;
    private final WorkoutCatalogueClient workoutClient;
    private final NutritionCatalogueClient nutritionClient;
    private final UserCalendar calendar;
    private final UserProfileClient userClient;
    private final AnalyticsProgressClient analyticsClient;

    public ReadFactsTool(RecommendationContextRepository contextRepository,
                         CoachFacts coachFacts,
                         ObjectMapper objectMapper,
                         WorkoutCatalogueClient workoutClient,
                         NutritionCatalogueClient nutritionClient,
                         UserCalendar calendar,
                         UserProfileClient userClient,
                         AnalyticsProgressClient analyticsClient) {
        this.contextRepository = contextRepository;
        this.coachFacts = coachFacts;
        this.objectMapper = objectMapper;
        this.workoutClient = workoutClient;
        this.nutritionClient = nutritionClient;
        this.calendar = calendar;
        this.userClient = userClient;
        this.analyticsClient = analyticsClient;
    }

    /** The name of the group that holds the streak as the analytics ledger counts it. */
    public static final String STREAK_GROUP = "streak";

    /**
     * The streak from the analytics ledger, the same count the Home page shows, or nothing when it cannot
     * be read. There is no stored copy to fall back on: a streak that may be days old is worse than none.
     */
    private Optional<Map<String, Object>> streak(CoachCallContext context, ZoneId zone) {
        try {
            JsonNode body = DiaryFigures.read(objectMapper.reader(),
                    analyticsClient.streaks(context.bearerToken(), zoneName(zone)));
            if (body == null || !body.path("currentStreakDays").isInt()) {
                return Optional.empty();
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("currentDays", body.path("currentStreakDays").asInt());
            if (body.path("bestStreakDays").isInt()) {
                group.put("bestDays", body.path("bestStreakDays").asInt());
            }
            return Optional.of(group);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** The name of the group that holds the current week so far. */
    public static final String CURRENT_WEEK_GROUP = "trainingThisWeek";

    /** The name of the group that holds the vitamins and minerals of the last seven days. */
    public static final String MICRONUTRIENTS_WEEK_GROUP = "micronutrientsWeek";

    /** The name of the group that holds what kind of day today is. */
    public static final String TODAY_GROUP = "today";

    static final String CURRENT_WEEK_PERIOD = "CURRENT_WEEK_SO_FAR";

    private static final DateTimeFormatter SPOKEN_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    private static final DateTimeFormatter SPOKEN_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    /** Where the last full week's counts came from when the weekly recap had not covered it yet. */
    static final String SOURCE_WORKOUT_LOG = "WORKOUT_LOG";

    @Override
    public String name() {
        return "readFacts";
    }

    /**
     * The current week as workout-service reports it, or nothing when it cannot be reached or read.
     * Both the week's totals and today's kind of day come out of this one response.
     */
    private Optional<JsonNode> weeklyInsight(CoachCallContext context, String zone) {
        try {
            String body = workoutClient.weeklyInsight(context.bearerToken(), zone);
            return body == null ? Optional.empty() : Optional.of(objectMapper.readTree(body));
        } catch (JsonProcessingException | RuntimeException ex) {
            // The current week is an addition. A turn is still answered from the rest of the facts.
            return Optional.empty();
        }
    }

    /**
     * Today's diary figures and the vitamins and minerals of the last seven days, read from
     * nutrition-service. Everything here is an addition: any call that fails, or any field an older
     * service does not send, leaves that part out and the facts are returned as they were.
     */
    private void addDiaryFigures(CoachCallContext context, ZoneId zone, JsonNode summary, Map<String, Object> facts) {
        try {
            LocalDate today = calendar.today(zone);
            String token = context.bearerToken();
            JsonNode todayNutrients = DiaryFigures.read(objectMapper.reader(),
                    nutritionClient.nutrientsSummary(token, today, today, zoneName(zone)));
            Map<String, Object> diary = DiaryFigures.day(summary, todayNutrients);
            if (!diary.isEmpty()) {
                Object existing = facts.get("nutritionToday");
                Map<String, Object> nutritionToday = new LinkedHashMap<>();
                if (existing instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> nutritionToday.put(String.valueOf(key), value));
                }
                nutritionToday.putAll(diary);
                facts.put("nutritionToday", nutritionToday);
            }
            JsonNode week = DiaryFigures.read(objectMapper.reader(),
                    nutritionClient.nutrientsSummary(token, today.minusDays(6), today, zoneName(zone)));
            Map<String, Object> micronutrients = DiaryFigures.micronutrients(week);
            if (!micronutrients.isEmpty()) {
                facts.put(MICRONUTRIENTS_WEEK_GROUP, micronutrients);
            }
        } catch (RuntimeException ex) {
            // The diary is an addition. A turn is still answered from the rest of the facts.
        }
    }

    /**
     * The zone this turn speaks in: the one the client reported, else the stored profile zone, else
     * UTC. Dates the coach says and the live reads it makes all use the same one.
     */
    private ZoneId zoneOf(CoachCallContext context, RecommendationContextEntity stored) {
        String reported = context.timezone();
        if (reported != null && !reported.isBlank()) {
            try {
                return ZoneId.of(reported);
            } catch (DateTimeException ex) {
                // An unknown zone name falls through to the stored one.
            }
        }
        if (stored != null) {
            return UserTimeZones.resolve(stored.getTimezone());
        }
        return calendar.zone(context.userId());
    }

    /**
     * The goal pace as the Progress reading sees it, read live from user-service with the caller's
     * token, or empty when that read fails or holds nothing, in which case the stored pace stays.
     */
    private Optional<Map<String, Object>> livePace(CoachCallContext context, ZoneId zone) {
        try {
            ProgressInsight insight = userClient.progressInsight(context.bearerToken(), zoneName(zone));
            Map<String, Object> pace = ProgressFactsBuilder.paceFacts(insight);
            return pace.isEmpty() ? Optional.empty() : Optional.of(pace);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** The zone as the other services expect it: UTC is written "UTC", not the offset id "Z". */
    private static String zoneName(ZoneId zone) {
        return zone.getId().equals("Z") ? "UTC" : zone.getId();
    }

    private static Optional<Map<String, Object>> currentWeek(JsonNode week) {
        if (!week.path("completedSessions").isInt() || !week.path("totalMinutes").isInt()) {
            return Optional.empty();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("period", CURRENT_WEEK_PERIOD);
        if (week.path("weekStart").isTextual()) {
            group.put("weekStart", week.path("weekStart").asText());
        }
        group.put("sessions", week.path("completedSessions").asInt());
        group.put("totalMinutes", week.path("totalMinutes").asInt());
        return Optional.of(group);
    }

    /**
     * Names the last full week by its dates, and keeps a recap that is older than it from being quoted as
     * "last week".
     *
     * <p>The training group comes from the analytics weekly recap. A recap is emitted after the week ends,
     * and a stack that was off when it ran left an older week in its place. When the recap covers the last
     * full week the group only gains its dates. When it covers an older one the group is rebuilt from the
     * previous-week counts of workout-service's weekly insight, which always means the last full week; the
     * targets carry over, the recap's day counts do not. Without that insight the stale group is dropped,
     * since a count from an older week said as last week's is worse than no count.
     */
    private static void lastFullWeek(Map<String, Object> facts, LocalDate recapWeekStart, LocalDate today,
                                     Optional<JsonNode> weekly) {
        LocalDate lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        Object existing = facts.get("training");
        Map<String, Object> training = new LinkedHashMap<>();
        if (existing instanceof Map<?, ?> map) {
            map.forEach((key, value) -> training.put(String.valueOf(key), value));
        }
        if (lastWeekStart.equals(recapWeekStart)) {
            if (!training.isEmpty()) {
                facts.put("training", withDates(training, lastWeekStart));
            }
            return;
        }
        Optional<JsonNode> previous = weekly.filter(week -> week.path("previousWeekCompletedSessions").isInt()
                && week.path("previousWeekTotalMinutes").isInt()
                && (!week.path("weekStart").isTextual()
                        || week.path("weekStart").asText().equals(lastWeekStart.plusWeeks(1).toString())));
        if (previous.isEmpty()) {
            facts.remove("training");
            facts.remove("weekStart");
            return;
        }
        Map<String, Object> rebuilt = new LinkedHashMap<>();
        rebuilt.put("period", CoachFacts.TRAINING_PERIOD);
        rebuilt.put("source", SOURCE_WORKOUT_LOG);
        rebuilt.put("workoutCount", previous.get().path("previousWeekCompletedSessions").asInt());
        rebuilt.put("totalMinutes", previous.get().path("previousWeekTotalMinutes").asInt());
        for (String carried : new String[] {"targetDaysPerWeek", "targetBasis"}) {
            if (training.containsKey(carried)) {
                rebuilt.put(carried, training.get(carried));
            }
        }
        facts.put("training", withDates(rebuilt, lastWeekStart));
        facts.put("weekStart", lastWeekStart.toString());
    }

    /** The group with its Monday, its Sunday and the two said aloud, "28 September to 4 October". */
    private static Map<String, Object> withDates(Map<String, Object> group, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        group.put("weekStart", weekStart.toString());
        group.put("weekEnd", weekEnd.toString());
        group.put("dates", weekStart.format(SPOKEN_DATE) + " to " + weekEnd.format(SPOKEN_DATE));
        return group;
    }

    /** What kind of day today is, where that comes from, and the next training day when there is one. */
    private static Optional<Map<String, Object>> today(JsonNode week) {
        JsonNode today = week.path("today");
        if (!today.path("kind").isTextual() || !today.path("label").isTextual()) {
            return Optional.empty();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("kind", today.path("kind").asText());
        group.put("label", today.path("label").asText());
        if (today.path("source").isTextual()) {
            group.put("source", today.path("source").asText());
        }
        if (today.path("title").isTextual()) {
            group.put("title", today.path("title").asText());
        }
        if (today.path("estimatedMinutes").isInt()) {
            group.put("estimatedMinutes", today.path("estimatedMinutes").asInt());
        }
        JsonNode next = week.path("nextTraining");
        if (next.path("date").isTextual() && next.path("label").isTextual()) {
            Map<String, Object> nextGroup = new LinkedHashMap<>();
            nextGroup.put("date", next.path("date").asText());
            // The model repeats what it is given, and a day of the week reads better than a date.
            spokenDay(next.path("date").asText()).ifPresent(day -> nextGroup.put("day", day));
            nextGroup.put("label", next.path("label").asText());
            group.put("next", nextGroup);
        }
        return Optional.of(group);
    }

    /** A date as it is said aloud, "Friday 2 October", or nothing when the text is not a date. */
    private static Optional<String> spokenDay(String isoDate) {
        try {
            return Optional.of(LocalDate.parse(isoDate).format(SPOKEN_DAY));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static ToolResult noFactsYet() {
        return ToolResult.of("no facts yet",
                "This user has no computed facts yet, which means they have not logged enough "
                        + "for the rules to fire. There are no numbers to quote.");
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readFacts()
                  no arguments
                  returns the user's computed facts as JSON: training, nutritionToday, goalPace,
                  streak, personalRecord, foodGroupWeek, focusAreas, today, micronutrientsWeek.
                  Every number here is already calculated, so quote it and never recompute it. When the
                  service has them, nutritionToday also carries today's fibre, sugar, saturated fat,
                  sodium and potassium against their references, water, protein at each meal and the
                  lowest vitamins and minerals, and micronutrientsWeek the lowest three over the last
                  seven days and any average above its upper limit. The training group describes
                  the last full week, Monday to Sunday, and names it by its dates. The trainingThisWeek group describes the
                  current week so far. Say which of the two weeks a count belongs to. The today group
                  says what kind of day today is (strength, cardio, mobility, a sport by name, rest, or
                  open) and where that comes from (done, in progress, the plan, or a habit of the last
                  four weeks). When you name the next training day, use its day, not its date.""";
    }

    /**
     * Today's daily summary, read once per call: the intake the rules quote and the diary figures
     * both come out of it, so the two can never disagree. Null when it cannot be read.
     */
    private JsonNode dailySummary(CoachCallContext context, ZoneId zone) {
        try {
            return DiaryFigures.read(objectMapper.reader(),
                    nutritionClient.dailySummary(context.bearerToken(), calendar.today(zone), zoneName(zone)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        Optional<RecommendationContextEntity> entity = contextRepository.findById(context.userId());
        if (entity.isEmpty()) {
            return noFactsYet();
        }
        ZoneId zone = zoneOf(context, entity.get());
        JsonNode summary = dailySummary(context, zone);
        TodayIntake intake = DiaryTodayIntakeSource.fromSummary(summary, calendar.today(zone));
        Map<String, Object> facts = new LinkedHashMap<>(coachFacts.assemble(entity.get(), null, intake));

        if (facts.isEmpty()) {
            return noFactsYet();
        }

        livePace(context, zone).ifPresent(pace -> facts.put("goalPace", pace));
        Optional<JsonNode> weekly = weeklyInsight(context, zoneName(zone));
        weekly.ifPresent(week -> {
            currentWeek(week).ifPresent(group -> facts.put(CURRENT_WEEK_GROUP, group));
            today(week).ifPresent(group -> facts.put(TODAY_GROUP, group));
        });
        lastFullWeek(facts, entity.get().getRecentWeekStart(), calendar.today(zone), weekly);
        streak(context, zone).ifPresent(group -> facts.put(STREAK_GROUP, group));

        addDiaryFigures(context, zone, summary, facts);

        try {
            return ToolResult.of(facts.size() + " fact groups",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
        } catch (JsonProcessingException ex) {
            return ToolResult.of("facts unreadable",
                    "The computed facts could not be serialised. Say that you cannot see them.");
        }
    }
}
