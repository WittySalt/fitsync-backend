package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns one workout-service training insight into the numbered facts a reading is built and checked
 * against.
 *
 * <p>Pure and deterministic on purpose: nothing here calls a model. A fact is either present in the
 * upstream data or it is not, and the numbering exists only to give the model something short to cite
 * and the validator something to check citations against. Numbers are pre-formatted here rather than
 * left to the model, because "the model copies a number exactly" is only checkable if there is an
 * exact string to compare against.
 */
@Component
public class ReadingFactsBuilder {

    private static final Map<String, String> MUSCLE_GROUP_LABELS = Map.ofEntries(
            Map.entry("FULL_BODY", "whole-body"),
            Map.entry("CHEST", "chest"),
            Map.entry("BACK", "back"),
            Map.entry("SHOULDERS", "shoulder"),
            Map.entry("ARMS", "arm"),
            Map.entry("CORE", "core"),
            Map.entry("GLUTES", "glute"),
            Map.entry("LEGS", "leg"),
            Map.entry("CARDIO", "cardio"));

    private static final int MAX_TEXT_LENGTH = 80;

    public List<ReadingFact> build(TrainingInsight insight) {
        List<ReadingFact> facts = new ArrayList<>();
        addVolumePerSession(facts, insight);
        addSessionLength(facts, insight);
        addRecord(facts, insight);
        addMostTrained(facts, insight);
        return List.copyOf(facts);
    }

    private void addVolumePerSession(List<ReadingFact> facts, TrainingInsight insight) {
        BigDecimal volume = insight.volumePerSessionKg();
        if (volume == null) {
            return;
        }
        BigDecimal earlier = insight.earlierVolumePerSessionKg();
        String detail = earlier == null ? "" : wholeWithCommas(earlier) + " kg the week before";
        String note = earlier == null ? "" : percentChangeNote(volume, earlier);
        facts.add(new ReadingFact(facts.size() + 1, "VOLUME_PER_SESSION", "Weight moved per session",
                wholeWithCommas(volume), "kg", volume, note, detail));
    }

    private void addSessionLength(List<ReadingFact> facts, TrainingInsight insight) {
        Integer minutes = insight.minutesPerLiftingSession();
        if (minutes == null) {
            return;
        }
        Integer earlier = insight.earlierMinutesPerLiftingSession();
        String detail = earlier == null ? "" : earlier + " min the week before";
        String note = earlier == null ? "" : minuteChangeNote(minutes, earlier);
        facts.add(new ReadingFact(facts.size() + 1, "SESSION_LENGTH", "Session length",
                String.valueOf(minutes), "min", BigDecimal.valueOf(minutes), note, detail));
    }

    private void addRecord(List<ReadingFact> facts, TrainingInsight insight) {
        TrainingInsight.RecordInfo record = insight.personalRecord();
        if (record == null) {
            return;
        }
        String exerciseName = trimAndCut(record.exerciseName());
        BigDecimal previousBest = record.previousBestKg();
        String detail = previousBest == null ? "" : upToOneDecimal(previousBest) + " kg before";
        String note = record.achievedOn() == null
                ? ""
                : record.achievedOn().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        facts.add(new ReadingFact(facts.size() + 1, "RECORD", exerciseName + " record",
                upToOneDecimal(record.weightKg()), "kg", record.weightKg(), note, detail));
    }

    private void addMostTrained(List<ReadingFact> facts, TrainingInsight insight) {
        TrainingInsight.MostTrainedInfo mostTrained = insight.mostTrained();
        if (mostTrained == null) {
            return;
        }
        String muscleLabel = muscleGroupLabel(mostTrained.muscleGroup());
        int sessionsInPeriod = insight.sessions() == null ? 0 : insight.sessions();
        String detail = mostTrained.exerciseNames().isEmpty() ? "" : trimAndCut(mostTrained.exerciseNames().get(0));
        String note = mostTrained.sessions() != null && mostTrained.sessions().equals(sessionsInPeriod)
                ? "Every one"
                : "";
        facts.add(new ReadingFact(facts.size() + 1, "MOST_TRAINED", "Sessions with " + muscleLabel + " work",
                String.valueOf(mostTrained.sessions()), "of " + sessionsInPeriod,
                mostTrained.sessions() == null ? null : BigDecimal.valueOf(mostTrained.sessions()), note, detail));
    }

    private static String percentChangeNote(BigDecimal current, BigDecimal earlier) {
        if (earlier.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        BigDecimal percent = current.subtract(earlier)
                .divide(earlier, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        int rounded = percent.setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (rounded == 0) {
            return "Unchanged";
        }
        return rounded > 0 ? "Up " + rounded + "%" : "Down " + Math.abs(rounded) + "%";
    }

    private static String minuteChangeNote(int current, int earlier) {
        int diff = current - earlier;
        if (diff == 0) {
            return "Unchanged";
        }
        return diff > 0 ? "Up " + diff + " min" : "Down " + Math.abs(diff) + " min";
    }

    private static String muscleGroupLabel(String code) {
        if (code == null) {
            return "";
        }
        String mapped = MUSCLE_GROUP_LABELS.get(code.toUpperCase(Locale.ROOT));
        return mapped != null ? mapped : code.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String trimAndCut(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() > MAX_TEXT_LENGTH ? trimmed.substring(0, MAX_TEXT_LENGTH) : trimmed;
    }

    /**
     * Whole kilograms, rounded, with a thousands separator: {@code 1923.4} becomes {@code "1,923"}.
     *
     * <p>Package-private rather than private so {@link RuleReadingWriter} can format the earlier
     * volume value the same way the facts themselves are formatted, instead of duplicating this
     * rounding and grouping logic.
     */
    static String wholeWithCommas(BigDecimal value) {
        long rounded = value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        return NumberFormat.getIntegerInstance(Locale.US).format(rounded);
    }

    /**
     * At most one decimal place, with a trailing {@code .0} dropped: {@code 20.00} becomes {@code "20"}.
     *
     * <p>Package-private rather than private so {@link RuleReadingWriter} can format a previous
     * personal best the same way the record fact's own value is formatted, instead of duplicating
     * this rounding logic.
     */
    static String upToOneDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal scaled = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0);
        }
        return scaled.toPlainString();
    }
}
