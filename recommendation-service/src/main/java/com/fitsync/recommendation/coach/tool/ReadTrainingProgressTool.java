package com.fitsync.recommendation.coach.tool;

import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.MAX_ITEMS;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.activityName;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.line;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.number;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.summaryLines;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.text;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.withPrevious;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.words;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * The Training page for a period, read from workout-service: sessions, minutes and load against the
 * period before, the activities, the estimated one-rep max of the main lifts, hard sets by muscle
 * group and the records.
 *
 * <p>The lift ratios are whatever workout-service reports. The body weight the page sends in its own
 * header is not forwarded, so a ratio absent from the response stays absent here.
 */
@Component
public class ReadTrainingProgressTool extends ProgressReadTool {

    private final WorkoutCatalogueClient client;

    public ReadTrainingProgressTool(WorkoutCatalogueClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        super(objectMapper, calendar);
        this.client = client;
    }

    @Override
    public String name() {
        return "readTrainingProgress";
    }

    @Override
    public String promptSignature() {
        return """
                readTrainingProgress(from?, to?)
                  from, to: dates like 2026-09-01, at most 370 days apart; default the last 28 days
                  returns the Training page for that period: sessions, minutes and load against the period
                  before, activities, estimated one-rep max of the main lifts, hard sets a week by muscle
                  group, records""";
    }

    @Override
    protected String subject() {
        return "Training progress";
    }

    @Override
    protected String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period) {
        return client.trainingProgress(context.bearerToken(), context.timezone(), period.from(), period.to());
    }

    @Override
    protected String render(JsonNode body, ProgressToolSupport.Period period) {
        StringBuilder out = new StringBuilder();
        JsonNode hero = body.path("hero");
        String sessions = withPrevious(hero.path("sessions"), "");
        String minutes = withPrevious(hero.path("minutes"), "");
        String load = withPrevious(hero.path("load"), "");
        String records = number(hero.path("records").path("value"));
        if (sessions != null) {
            line(out, "Sessions: " + sessions + ".");
        }
        if (minutes != null) {
            line(out, "Minutes trained: " + minutes + ".");
        }
        if (load != null) {
            String share = estimatedShare(hero.path("load").path("estimatedShare"));
            line(out, "Training load (effort times minutes): " + load + "."
                    + (share == null ? "" : " " + share + "% of it uses a default effort."));
        }
        if (records != null) {
            List<String> names = names(hero.path("records").path("exercises"));
            line(out, "Records: " + records + (names.isEmpty() ? "" : ", on " + String.join(", ", names)) + ".");
        }
        String form = body.path("load").path("form").asText("");
        if (!form.isBlank()) {
            line(out, "Form, from the last six weeks of load against last week's: " + words(form) + ".");
        }
        ProgressToolSupport.appendHighlights(out, body.path("highlights"));
        appendActivities(out, body.path("activities"));
        appendLifts(out, body.path("lifts"));
        appendMuscles(out, body.path("muscles"));
        appendRecords(out, body.path("records"));
        if (out.length() == 0) {
            return "";
        }
        return "Training from " + period.describe() + previousPeriod(body) + ".\n" + out;
    }

    private static String previousPeriod(JsonNode body) {
        String from = body.path("previous").path("from").asText("");
        String to = body.path("previous").path("to").asText("");
        return from.isBlank() || to.isBlank() ? "" : ". The period before is " + from + " to " + to;
    }

    /** A share of 0 to 1 is written as a percentage; a share already over 1 is taken as one. */
    private static String estimatedShare(JsonNode share) {
        if (!share.isNumber()) {
            return null;
        }
        BigDecimal value = share.decimalValue();
        if (value.compareTo(BigDecimal.ONE) <= 0) {
            value = value.multiply(BigDecimal.valueOf(100));
        }
        if (value.signum() == 0) {
            return null;
        }
        return ProgressToolSupport.format(value.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        for (JsonNode name : array) {
            String cleaned = ProgressToolSupport.clean(name.asText(""));
            if (!cleaned.isEmpty()) {
                names.add(cleaned);
            }
            if (names.size() >= MAX_ITEMS) {
                break;
            }
        }
        return names;
    }

    private static void appendActivities(StringBuilder out, JsonNode activities) {
        List<String> lines = new ArrayList<>();
        for (JsonNode activity : activities) {
            String name = text(activity, "name");
            if (name.isEmpty()) {
                name = activityName(activity.path("key").asText(""));
            }
            StringBuilder item = new StringBuilder("- ").append(name).append(':');
            String sessions = number(activity.path("sessions"));
            String minutes = number(activity.path("minutes"));
            if (sessions != null) {
                item.append(' ').append(sessions).append(" sessions");
            }
            if (minutes != null) {
                item.append(sessions == null ? " " : ", ").append(minutes).append(" minutes");
            }
            item.append('.');
            String context = text(activity, "context");
            if (!context.isEmpty()) {
                item.append(' ').append(context).append('.');
            }
            List<String> summary = summaryLines(activity.path("summary"));
            if (!summary.isEmpty()) {
                item.append(' ').append(String.join("; ", summary)).append('.');
            }
            lines.add(item.toString());
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            line(out, "Activities:");
            lines.forEach(activity -> line(out, activity));
        }
    }

    private static void appendLifts(StringBuilder out, JsonNode lifts) {
        List<String> lines = new ArrayList<>();
        for (JsonNode lift : lifts) {
            String name = text(lift, "name");
            JsonNode latest = ProgressToolSupport.last(lift.path("e1rm"));
            String e1rm = number(latest.path("value"));
            if (name.isEmpty() || e1rm == null) {
                continue;
            }
            StringBuilder item = new StringBuilder("- ").append(name).append(": ").append(e1rm).append(" kg");
            String date = latest.path("date").asText("");
            if (!date.isBlank()) {
                item.append(" on ").append(ProgressToolSupport.clean(date));
            }
            String change = number(lift.path("changePct"));
            if (change != null) {
                item.append(", a change of ").append(change).append("% over the period");
            }
            JsonNode best = lift.path("bestSet");
            String weight = number(best.path("weightKg"));
            String reps = number(best.path("reps"));
            if (weight != null && reps != null) {
                item.append(", best set ").append(weight).append(" kg for ").append(reps).append(" reps");
            }
            JsonNode relative = lift.path("relative");
            String ratio = number(relative.path("ratio"));
            if (ratio != null) {
                item.append(", ").append(ratio).append(" times body weight");
            }
            String level = relative.path("level").asText("");
            if (!level.isBlank()) {
                item.append(", level ").append(words(level));
                String next = relative.path("nextLevel").asText("");
                String nextAt = number(relative.path("nextAtKg"));
                if (!next.isBlank() && nextAt != null) {
                    item.append(", the next level ").append(words(next)).append(" starts at ").append(nextAt).append(" kg");
                }
            }
            lines.add(item.append('.').toString());
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            line(out, "Estimated one-rep max (weight times one plus reps over 30, from the heaviest set) of the main lifts:");
            lines.forEach(lift -> line(out, lift));
        }
    }

    private static void appendMuscles(StringBuilder out, JsonNode muscles) {
        List<String> lines = new ArrayList<>();
        for (JsonNode group : muscles.path("groups")) {
            String sets = number(group.path("setsPerWeek"));
            String name = words(group.path("group").asText(""));
            if (sets == null || name.isEmpty()) {
                continue;
            }
            lines.add("- " + name + ": " + sets);
        }
        if (lines.isEmpty()) {
            return;
        }
        String weeks = number(muscles.path("weeksCounted"));
        String low = number(muscles.path("usualRange").path("low"));
        String high = number(muscles.path("usualRange").path("high"));
        line(out, "Hard sets a week by muscle group" + (weeks == null ? "" : " over " + weeks + " weeks")
                + (low == null || high == null ? "" : ", where " + low + " to " + high + " is the usual range") + ":");
        lines.forEach(group -> line(out, group));
    }

    private static void appendRecords(StringBuilder out, JsonNode records) {
        List<String> lines = new ArrayList<>();
        for (JsonNode record : records) {
            String name = text(record, "name");
            String value = number(record.path("value"));
            if (name.isEmpty() || value == null) {
                continue;
            }
            String unit = text(record, "unit");
            String delta = ProgressToolSupport.absolute(record.path("delta"));
            String date = record.path("date").asText("");
            lines.add("- " + (date.isBlank() ? "" : ProgressToolSupport.clean(date) + " ") + name + ": " + value
                    + (unit.isEmpty() ? "" : " " + unit.toLowerCase(java.util.Locale.ROOT))
                    + (delta == null || ProgressToolSupport.sign(record.path("delta")) == 0
                    ? "" : ", " + delta + " above the record before"));
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            line(out, "Records set in the period:");
            lines.forEach(record -> line(out, record));
        }
    }
}
