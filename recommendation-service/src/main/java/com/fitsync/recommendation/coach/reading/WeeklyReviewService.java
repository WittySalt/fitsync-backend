package com.fitsync.recommendation.coach.reading;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.tool.UserCalendar;
import org.springframework.stereotype.Service;

/**
 * The weekly reviews on the Progress page: one short review per Monday week, newest first.
 *
 * <p>A review follows the page readings' pattern throughout. Its facts are built in pure code from the
 * Training, Goal and Food endpoints for that week, read with the caller's own token and zone. A rule
 * writer turns them into the default review, checked by {@link ReadingValidator}; when a model is
 * configured, a model review is generated in the background through {@link CoachReadingService} with
 * the same validation, repair and remembered rejection, and replaces the rule review once accepted.
 * Rows live in the reading table under surface {@code PROGRESS_WEEK}, keyed by the week's Monday, so
 * the account purge removes them with the readings.
 *
 * <p>A week that has ended is read once, the first time it is asked for after it ended, and its facts
 * are stored with the row; every later request serves it from the row without reading three services.
 * The current week is read on every request and labelled "so far"; when its facts change, the facts
 * hash changes and the review is written again. A week with fewer than two facts is {@code NONE}.
 * When one of the three endpoints cannot be read, the week is still shown from what could be read,
 * but nothing is stored, so it is read again next time.
 */
@Service
public class WeeklyReviewService {

    public static final int DEFAULT_WEEKS = 8;
    public static final int MAX_WEEKS = 26;

    /** How many weeks a review needs facts for before there is anything to write. */
    static final int MINIMUM_FACTS = 2;

    /** How many model reviews one request may start, so asking for 26 weeks cannot start 26 generations. */
    static final int MAX_GENERATIONS_PER_REQUEST = 2;

    /** The period label of a week that has ended, in the facts hash and the model prompt. */
    static final String WEEK = "WEEK";

    /** The period label of the week still going. */
    static final String WEEK_SO_FAR = "WEEK_SO_FAR";

    private static final long PENDING_ABANDON_SECONDS = 60L;

    /** The facts column holds at most this many characters; a longer list is shown but not stored. */
    private static final int MAX_FACTS_JSON = 4000;

    private static final String FACTS_NOTE = " The period label is WEEK for a Monday to Sunday week that has "
            + "ended, or WEEK_SO_FAR for the current week, which is still going; for WEEK_SO_FAR write about "
            + "the week so far. A note of Up, Down or Unchanged compares a fact with the week before, and "
            + "Calories a day and Protein a day count only the days with food logged that are over; a fact whose "
            + "detail is today so far holds only today, which is still going, and is never compared with "
            + "anything. A fact labelled Balance "
            + "a day says in its note whether what was eaten was Under or Over the energy used.";

    private static final String EXAMPLE = """
            period: WEEK
            facts: [
              {"number":1,"label":"Sessions","value":"4","unit":"sessions","note":"Up 1","detail":"3 the week before"},
              {"number":2,"label":"Minutes trained","value":"190","unit":"min","note":"Up 40 min","detail":"150 min the week before"},
              {"number":3,"label":"Protein a day","value":"128","unit":"g","note":"Up 6%","detail":"on 6 logged days"}
            ]

            Example output:
            {"headline": "A fuller week of training.", "body": "You trained 4 sessions [1] over 190 minutes [2], and ate 128 g of protein a day on logged days [3]."}""";

    static final String SYSTEM_PROMPT = ReadingPrompt.compose("", ReadingPrompt.DIRECTION_RULE_END, FACTS_NOTE, EXAMPLE);

    private final WorkoutCatalogueClient workoutClient;
    private final AnalyticsProgressClient analyticsClient;
    private final WeeklyReviewFactsBuilder factsBuilder;
    private final WeeklyReviewWriter writer;
    private final CoachPageReadingStore store;
    private final CoachReadingService readingService;
    private final UserCalendar calendar;
    private final Clock clock;
    // Readers and writers are immutable, which a shared ObjectMapper is not.
    private final ObjectReader treeReader;
    private final ObjectReader factsReader;
    private final ObjectWriter factsWriter;

    public WeeklyReviewService(WorkoutCatalogueClient workoutClient,
                               AnalyticsProgressClient analyticsClient,
                               WeeklyReviewFactsBuilder factsBuilder,
                               WeeklyReviewWriter writer,
                               CoachPageReadingStore store,
                               CoachReadingService readingService,
                               UserCalendar calendar,
                               Clock clock,
                               ObjectMapper objectMapper) {
        this.workoutClient = workoutClient;
        this.analyticsClient = analyticsClient;
        this.factsBuilder = factsBuilder;
        this.writer = writer;
        this.store = store;
        this.readingService = readingService;
        this.calendar = calendar;
        this.clock = clock;
        this.treeReader = objectMapper.reader();
        this.factsReader = objectMapper.readerFor(
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReadingFact.class));
        this.factsWriter = objectMapper.writer();
    }

    /**
     * The reviews of the last {@code weeks} Monday weeks, the current one first.
     *
     * @param timezone the zone from the client's X-Timezone header, or null to use the stored zone,
     *                 then UTC
     */
    public List<WeeklyReviewView> reviews(UUID userId, String bearerToken, String timezone, int weeks) {
        int count = Math.max(1, Math.min(weeks, MAX_WEEKS));
        ZoneId zone = zoneOf(userId, timezone);
        LocalDate thisMonday = calendar.today(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<LocalDate> mondays = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            mondays.add(thisMonday.minusWeeks(i));
        }
        Map<String, CoachPageReadingEntity> rows = store.findAll(userId, ReadingSurface.PROGRESS_WEEK,
                mondays.stream().map(LocalDate::toString).toList());

        Request request = new Request(userId, bearerToken, zone.getId(), Instant.now(clock));
        List<WeeklyReviewView> views = new ArrayList<>();
        for (LocalDate monday : mondays) {
            boolean current = monday.equals(thisMonday);
            WeeklyReviewView view = review(request, monday, current, rows.get(monday.toString()), zone);
            views.add(current ? view.withToday(request.today) : view);
        }
        return views;
    }

    private WeeklyReviewView review(Request request, LocalDate monday, boolean current, CoachPageReadingEntity stored,
                                    ZoneId zone) {
        LocalDate sunday = monday.plusDays(6);
        String key = monday.toString();
        CoachPageReadingEntity row = stored;

        List<ReadingFact> facts = null;
        String hash = null;
        boolean complete = false;

        // A week that ended is served from its row once that row was written after the week ended.
        Instant weekEndsAt = monday.plusDays(7).atStartOfDay(zone).toInstant();
        if (!current && row != null && row.getFactsJson() != null && row.getStartedAt() != null
                && !row.getStartedAt().isBefore(weekEndsAt)) {
            facts = readFacts(row.getFactsJson());
            hash = row.getFactsHash();
            complete = facts != null;
        }

        if (facts == null) {
            JsonNode training = request.trainingAvailable ? read(workoutClient.trainingProgress(
                    request.bearerToken, request.zoneId, monday, sunday)) : null;
            request.trainingAvailable = training != null;
            JsonNode goal = request.goalAvailable ? read(analyticsClient.goalProgress(
                    request.bearerToken, request.zoneId, monday, sunday)) : null;
            request.goalAvailable = goal != null;
            JsonNode food = request.foodAvailable ? read(analyticsClient.foodProgress(
                    request.bearerToken, request.zoneId, monday, sunday)) : null;
            request.foodAvailable = food != null;

            if (training == null && goal == null && food == null) {
                List<ReadingFact> earlier = row == null || row.getFactsJson() == null ? null : readFacts(row.getFactsJson());
                if (earlier == null) {
                    return none(monday, sunday, current, List.of());
                }
                facts = earlier;
                hash = row.getFactsHash();
            } else {
                facts = factsBuilder.build(training, goal, food, current);
                if (current) {
                    request.today = WeeklyReviewFactsBuilder.today(food);
                }
                hash = CoachReadingService.hash(current ? WEEK_SO_FAR : WEEK, monday, sunday, facts);
                complete = training != null && goal != null && food != null;
                String json = writeFacts(facts);
                if (complete && json != null) {
                    row = store.recordFacts(request.userId, ReadingSurface.PROGRESS_WEEK, key, hash, json, request.now);
                } else {
                    complete = false;
                }
            }
        }

        if (facts.size() < MINIMUM_FACTS) {
            return none(monday, sunday, current, facts);
        }

        boolean sameFacts = row != null && CoachPageReadingStore.hashesMatch(hash, row.getFactsHash());
        if (sameFacts && row.getOutcome() == ReadingOutcome.ACCEPTED && row.getHeadline() != null) {
            return new WeeklyReviewView(monday, sunday, current, WeeklyReviewStatus.READY, row.getHeadline(),
                    row.getBody(), citations(row.getCitations()), row.getSource(), facts);
        }

        boolean writing = complete && sameFacts && modelReviewOwed(request, row, key, hash, facts, current);

        Optional<RuleReadingWriter.RuleReading> rule = writer.write(facts, current);
        if (rule.isPresent()) {
            return new WeeklyReviewView(monday, sunday, current, WeeklyReviewStatus.READY, rule.get().headline(),
                    rule.get().body(), rule.get().citations(), "RULES", facts);
        }
        return writing
                ? new WeeklyReviewView(monday, sunday, current, WeeklyReviewStatus.WRITING, null, null, List.of(), null, facts)
                : none(monday, sunday, current, facts);
    }

    /**
     * Whether a model review is being written for these facts: one already running, or one started
     * here because none was asked for yet, a rejection has expired or an earlier attempt was abandoned.
     */
    private boolean modelReviewOwed(Request request, CoachPageReadingEntity row, String key, String hash,
                                    List<ReadingFact> facts, boolean current) {
        ReadingOutcome outcome = row.getOutcome();
        Instant now = request.now;
        if (outcome == ReadingOutcome.PENDING && row.getStartedAt() != null
                && Duration.between(row.getStartedAt(), now).getSeconds() < PENDING_ABANDON_SECONDS) {
            return true;
        }
        boolean rejectionStands = outcome == ReadingOutcome.REJECTED
                && row.getExpiresAt() != null && now.isBefore(row.getExpiresAt());
        if (rejectionStands || request.generationsStarted >= MAX_GENERATIONS_PER_REQUEST) {
            return false;
        }
        boolean started = readingService.startGeneration(request.userId, ReadingSurface.PROGRESS_WEEK, key,
                SYSTEM_PROMPT, hash, facts, current ? WEEK_SO_FAR : WEEK, now);
        if (started) {
            request.generationsStarted++;
        }
        return started;
    }

    private static WeeklyReviewView none(LocalDate monday, LocalDate sunday, boolean current, List<ReadingFact> facts) {
        return new WeeklyReviewView(monday, sunday, current, WeeklyReviewStatus.NONE, null, null, List.of(), null, facts);
    }

    private ZoneId zoneOf(UUID userId, String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (DateTimeException ex) {
                // An unknown zone name falls through to the stored one.
            }
        }
        return calendar.zone(userId);
    }

    private JsonNode read(String body) {
        if (body == null) {
            return null;
        }
        try {
            JsonNode node = treeReader.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<ReadingFact> readFacts(String json) {
        try {
            return factsReader.readValue(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String writeFacts(List<ReadingFact> facts) {
        try {
            String json = factsWriter.writeValueAsString(facts);
            return json.length() > MAX_FACTS_JSON ? null : json;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static List<Integer> citations(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : csv.split(",")) {
            numbers.add(Integer.valueOf(part.strip()));
        }
        return numbers;
    }

    /**
     * What one request carries from week to week: who is asking, and which endpoints already failed,
     * so a service that is down costs one timeout per request rather than one per week.
     */
    private static final class Request {
        private final UUID userId;
        private final String bearerToken;
        private final String zoneId;
        private final Instant now;
        private boolean trainingAvailable = true;
        private boolean goalAvailable = true;
        private boolean foodAvailable = true;
        private int generationsStarted;
        private WeeklyReviewView.Today today;

        private Request(UUID userId, String bearerToken, String zoneId, Instant now) {
            this.userId = userId;
            this.bearerToken = bearerToken;
            this.zoneId = zoneId;
            this.now = now;
        }

        @Override
        public String toString() {
            return "Request[userId=" + userId + ", token=redacted]";
        }
    }
}
