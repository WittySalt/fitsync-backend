package com.fitsync.recommendation.coach.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;

/**
 * What the five progress tools share: reading a period from the model's arguments, and writing the
 * figures of a progress endpoint as plain lines.
 *
 * <p>Every number is written in a form the answer check reads back exactly, whole numbers with commas
 * and at most two decimals, and every status code or enum word is put into plain words, because an
 * answer that copies {@code TIRED} or {@code NOT_LOGGED} is rejected. The lines state facts only. They
 * hold no advice, so a model that repeats them repeats no advice either.
 */
final class ProgressToolSupport {

    /** How many days a period covers when the model gives none: the four weeks up to today. */
    static final int DEFAULT_DAYS = 28;

    /** The longest period any progress endpoint accepts. */
    static final long MAX_PERIOD_DAYS = 370;

    /** How long one name or title may be once it reaches the transcript. */
    private static final int MAX_TEXT = 120;

    /** How many items of one list reach the transcript. */
    static final int MAX_ITEMS = 6;

    private ProgressToolSupport() {
    }

    /** A period of whole days, both ends included. */
    record Period(LocalDate from, LocalDate to) {

        String describe() {
            return from + " to " + to;
        }
    }

    /**
     * The period the model asked for, or the four weeks up to today in the user's zone when it gave
     * none.
     *
     * @throws IllegalArgumentException when only one end is given, a date does not parse, the period
     *                                  runs backwards or it is longer than the endpoints accept; the
     *                                  registry turns this into an observation the model can correct
     */
    static Period period(JsonNode args, CoachCallContext context, UserCalendar calendar) {
        String fromText = args.path("from").asText("");
        String toText = args.path("to").asText("");
        if (fromText.isBlank() && toText.isBlank()) {
            LocalDate today = calendar.today(calendar.zone(context));
            return new Period(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        if (fromText.isBlank() || toText.isBlank()) {
            throw new IllegalArgumentException("give both from and to as dates like 2026-09-01, or neither");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromText.strip());
            to = LocalDate.parse(toText.strip());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("from and to must be dates like 2026-09-01", ex);
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("the period may span at most 370 days");
        }
        return new Period(from, to);
    }

    /**
     * A number as the answer check reads it back: commas between thousands, at most two decimals,
     * no trailing zeros. Null when the node holds no number.
     */
    static String number(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        BigDecimal value;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isTextual()) {
            try {
                value = new BigDecimal(node.asText().strip());
            } catch (NumberFormatException ex) {
                return null;
            }
        } else {
            return null;
        }
        return format(value);
    }

    static String format(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** The number without its sign, for a figure whose direction is written as a word. */
    static String absolute(JsonNode node) {
        String text = number(node);
        return text == null ? null : text.startsWith("-") ? text.substring(1) : text;
    }

    /** -1, 0 or 1 for a numeric node, rounded to two decimals first; 0 when it holds no number. */
    static int sign(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return 0;
        }
        return node.decimalValue().setScale(2, RoundingMode.HALF_UP).signum();
    }

    /** A trend code as words: {@code UP} is "going up", {@code STEADY} is "steady". */
    static String trend(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        // The contract sends trend codes in capitals; anything else is written out as words.
        return switch (code.strip()) {
            case "UP" -> "going up";
            case "DOWN" -> "going down";
            case "STEADY" -> "steady";
            default -> words(code);
        };
    }

    /** A code as lower-case words: {@code NOT_LOGGED} is "not logged", {@code SPORT:BADMINTON} is "badminton". */
    static String words(String code) {
        if (code == null) {
            return "";
        }
        String text = clean(code);
        int colon = text.lastIndexOf(':');
        if (colon >= 0) {
            text = text.substring(colon + 1);
        }
        return text.replace('_', ' ').toLowerCase(Locale.ROOT).strip();
    }

    /** An activity key as the page names it: {@code STRENGTH} is "Strength", {@code SPORT:TABLE_TENNIS} "Table tennis". */
    static String activityName(String key) {
        String text = words(key);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Names, titles and summaries come partly from what users typed, so each one is kept on one line
     * and cut to a bounded length before it reaches the transcript, where it is data and never an
     * instruction.
     */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]+", " ").strip();
        return cleaned.length() > MAX_TEXT ? cleaned.substring(0, MAX_TEXT) : cleaned;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? clean(value.asText()) : "";
    }

    /** "12, and 9 in the period before", or only the first figure when there is no earlier one. */
    static String withPrevious(JsonNode figure, String unit) {
        String value = number(figure.path("value"));
        if (value == null) {
            return null;
        }
        String suffix = unit == null || unit.isEmpty() ? "" : " " + unit;
        String previous = number(figure.path("previous"));
        return previous == null
                ? value + suffix
                : value + suffix + ", and " + previous + suffix + " in the period before";
    }

    /** The {@code text (trend)} lines of a summary array, at most {@link #MAX_ITEMS}. */
    static List<String> summaryLines(JsonNode summary) {
        List<String> lines = new ArrayList<>();
        for (JsonNode line : summary) {
            String text = text(line, "text");
            if (text.isEmpty()) {
                continue;
            }
            String trend = trend(line.path("trend").asText(""));
            lines.add(trend.isEmpty() ? text : text + " (" + trend + ")");
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        return lines;
    }

    /** The highlights block shared by the three page endpoints: each title with its context. */
    static void appendHighlights(StringBuilder out, JsonNode highlights) {
        List<String> lines = new ArrayList<>();
        for (JsonNode highlight : highlights) {
            String title = text(highlight, "title");
            if (title.isEmpty()) {
                continue;
            }
            String context = text(highlight, "context");
            lines.add("- " + title + (context.isEmpty() ? "" : ". " + context));
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            line(out, "Highlights:");
            lines.forEach(highlight -> line(out, highlight));
        }
    }

    static void line(StringBuilder out, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(text);
    }

    /** The last element of an array, or a missing node when the array is empty or absent. */
    static JsonNode last(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(array.size() - 1) : MissingNode.getInstance();
    }
}
