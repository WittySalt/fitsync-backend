package com.fitsync.recommendation.coach.tool;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.springframework.stereotype.Component;

/**
 * The user's computed facts: the last full week's training, today's nutrition, goal pace, logging streak,
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

    public ReadFactsTool(RecommendationContextRepository contextRepository,
                         CoachFacts coachFacts,
                         ObjectMapper objectMapper,
                         WorkoutCatalogueClient workoutClient,
                         NutritionCatalogueClient nutritionClient,
                         UserCalendar calendar) {
        this.contextRepository = contextRepository;
        this.coachFacts = coachFacts;
        this.objectMapper = objectMapper;
        this.workoutClient = workoutClient;
        this.nutritionClient = nutritionClient;
        this.calendar = calendar;
    }

    /** The name of the group that holds the current week so far. */
    public static final String CURRENT_WEEK_GROUP = "trainingThisWeek";

    /** The name of the group that holds the vitamins and minerals of the last seven days. */
    public static final String MICRONUTRIENTS_WEEK_GROUP = "micronutrientsWeek";

    /** The name of the group that holds what kind of day today is. */
    public static final String TODAY_GROUP = "today";

    static final String CURRENT_WEEK_PERIOD = "CURRENT_WEEK_SO_FAR";

    private static final String DEFAULT_TIMEZONE = "UTC";

    private static final DateTimeFormatter SPOKEN_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    @Override
    public String name() {
        return "readFacts";
    }

    /**
     * The current week as workout-service reports it, or nothing when it cannot be reached or read.
     * Both the week's totals and today's kind of day come out of this one response.
     */
    private Optional<JsonNode> weeklyInsight(CoachCallContext context, String timezone) {
        String zone = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
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
    private void addDiaryFigures(CoachCallContext context, Map<String, Object> facts) {
        try {
            ZoneId zone = calendar.zone(context.userId());
            LocalDate today = calendar.today(zone);
            String token = context.bearerToken();
            JsonNode summary = DiaryFigures.read(objectMapper.reader(),
                    nutritionClient.dailySummary(token, today, zone.getId()));
            JsonNode todayNutrients = DiaryFigures.read(objectMapper.reader(),
                    nutritionClient.nutrientsSummary(token, today, today, zone.getId()));
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
                    nutritionClient.nutrientsSummary(token, today.minusDays(6), today, zone.getId()));
            Map<String, Object> micronutrients = DiaryFigures.micronutrients(week);
            if (!micronutrients.isEmpty()) {
                facts.put(MICRONUTRIENTS_WEEK_GROUP, micronutrients);
            }
        } catch (RuntimeException ex) {
            // The diary is an addition. A turn is still answered from the rest of the facts.
        }
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
                  loggingStreak, personalRecord, foodGroupWeek, focusAreas, today, micronutrientsWeek.
                  Every number here is already calculated — quote it, never recompute it. When the
                  service has them, nutritionToday also carries today's fibre, sugar, saturated fat,
                  sodium and potassium against their references, water, protein at each meal and the
                  lowest vitamins and minerals, and micronutrientsWeek the lowest three over the last
                  seven days and any average above its upper limit. The training group describes
                  the last full week, Monday to Sunday. The trainingThisWeek group describes the
                  current week so far. Say which of the two weeks a count belongs to. The today group
                  says what kind of day today is (strength, cardio, mobility, a sport by name, rest, or
                  open) and where that comes from (done, in progress, the plan, or a habit of the last
                  four weeks). When you name the next training day, use its day, not its date.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        Optional<RecommendationContextEntity> entity = contextRepository.findById(context.userId());
        Map<String, Object> facts = new LinkedHashMap<>(entity
                .map(found -> coachFacts.assemble(found, null))
                .orElse(Map.of()));

        if (facts.isEmpty()) {
            return ToolResult.of("no facts yet",
                    "This user has no computed facts yet, which means they have not logged enough "
                            + "for the rules to fire. Encourage a first workout or meal rather than "
                            + "inventing numbers.");
        }

        weeklyInsight(context, entity.map(RecommendationContextEntity::getTimezone).orElse(null)).ifPresent(week -> {
            currentWeek(week).ifPresent(group -> facts.put(CURRENT_WEEK_GROUP, group));
            today(week).ifPresent(group -> facts.put(TODAY_GROUP, group));
        });

        addDiaryFigures(context, facts);

        try {
            return ToolResult.of(facts.size() + " fact groups",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
        } catch (JsonProcessingException ex) {
            return ToolResult.of("facts unreadable",
                    "The computed facts could not be serialised. Say that you cannot see them.");
        }
    }
}
