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

    /** Placeholder while a fact is being built; {@link #build} numbers the chosen facts afterwards. */
    private static final int UNNUMBERED = 0;

    private static final int MAX_FACTS = 4;

    public List<ReadingFact> build(TrainingInsight insight) {
        List<ReadingFact> lifting = new ArrayList<>();
        addVolumePerSession(lifting, insight);
        addSessionLength(lifting, insight);
        addRecord(lifting, insight);
        addMostTrained(lifting, insight);

        List<ReadingFact> cardio = new ArrayList<>();
        addCardioFacts(cardio, insight.cardio());
        ReadingFact mobility = mobilityTime(insight.mobility());
        ReadingFact sport = sportTime(insight.sport());

        List<ReadingFact> chosen = new ArrayList<>();
        if (!lifting.isEmpty() && cardio.isEmpty()) {
            chosen.addAll(lifting);
        } else if (!lifting.isEmpty()) {
            // A week with both kinds of training shows two of each; when the cardio side has only one
            // fact, the place it leaves free goes to a third lifting fact.
            chosen.addAll(lifting.subList(0, Math.min(2, lifting.size())));
            chosen.addAll(cardio.subList(0, Math.min(2, cardio.size())));
            if (cardio.size() == 1 && lifting.size() > 2) {
                chosen.add(lifting.get(2));
            }
        } else {
            chosen.addAll(cardio);
        }
        for (ReadingFact extra : new ReadingFact[] {mobility, sport}) {
            if (extra != null && chosen.size() < MAX_FACTS) {
                chosen.add(extra);
            }
        }
        if (chosen.size() > MAX_FACTS) {
            chosen = chosen.subList(0, MAX_FACTS);
        }

        List<ReadingFact> numbered = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            ReadingFact fact = chosen.get(i);
            numbered.add(new ReadingFact(i + 1, fact.key(), fact.label(), fact.value(), fact.unit(),
                    fact.numericValue(), fact.note(), fact.detail(), fact.card()));
        }
        return List.copyOf(numbered);
    }

    private void addCardioFacts(List<ReadingFact> facts, TrainingInsight.CardioInfo cardio) {
        if (cardio == null) {
            return;
        }
        BigDecimal distance = cardio.distancePerSessionKm();
        if (distance != null) {
            BigDecimal earlier = cardio.earlierDistancePerSessionKm();
            facts.add(new ReadingFact(UNNUMBERED, "CARDIO_DISTANCE", "Distance per cardio session",
                    upToTwoDecimals(distance), "km", distance,
                    earlier == null ? "" : percentChangeNote(distance, earlier),
                    earlier == null ? "" : upToTwoDecimals(earlier) + " km the week before"));
            BigDecimal pace = cardio.paceSecondsPerKm();
            if (pace != null) {
                BigDecimal earlierPace = cardio.earlierPaceSecondsPerKm();
                int seconds = wholeSeconds(pace);
                facts.add(new ReadingFact(UNNUMBERED, "CARDIO_PACE", "Pace", clock(seconds), "per km",
                        BigDecimal.valueOf(seconds),
                        earlierPace == null ? "" : paceChangeNote(seconds, wholeSeconds(earlierPace)),
                        earlierPace == null ? "" : clock(wholeSeconds(earlierPace)) + " per km the week before"));
            }
            return;
        }
        Integer minutes = cardio.minutesPerSession();
        if (minutes != null && minutes > 0) {
            Integer earlier = cardio.earlierMinutesPerSession();
            facts.add(new ReadingFact(UNNUMBERED, "CARDIO_LENGTH", "Cardio session length",
                    String.valueOf(minutes), "min", BigDecimal.valueOf(minutes),
                    earlier == null ? "" : minuteChangeNote(minutes, earlier),
                    earlier == null ? "" : earlier + " min the week before"));
        }
    }

    private static ReadingFact mobilityTime(TrainingInsight.MobilityInfo mobility) {
        if (mobility == null || mobility.minutes() == null || mobility.minutes() <= 0) {
            return null;
        }
        Integer earlier = mobility.earlierMinutes();
        return new ReadingFact(UNNUMBERED, "MOBILITY_TIME", "Mobility", String.valueOf(mobility.minutes()), "min",
                BigDecimal.valueOf(mobility.minutes()), sessionCount(mobility.sessions()),
                earlier == null ? "" : earlier + " min the week before");
    }

    /** The activity with the most minutes speaks for the sport group; the first one wins a tie. */
    private static ReadingFact sportTime(TrainingInsight.SportInfo sport) {
        if (sport == null) {
            return null;
        }
        String label = "Sport";
        Integer minutes = sport.minutes();
        Integer sessions = sport.sessions();
        TrainingInsight.SportActivity top = null;
        for (TrainingInsight.SportActivity activity : sport.activities()) {
            if (activity.minutes() != null && (top == null || activity.minutes() > top.minutes())) {
                top = activity;
            }
        }
        if (top != null) {
            String named = trimAndCut(top.label());
            label = named.isEmpty() ? label : named;
            minutes = top.minutes();
            sessions = top.sessions();
        }
        if (minutes == null || minutes <= 0) {
            return null;
        }
        return new ReadingFact(UNNUMBERED, "SPORT_TIME", label, String.valueOf(minutes), "min",
                BigDecimal.valueOf(minutes), sessionCount(sessions), "");
    }

    private static String sessionCount(Integer sessions) {
        if (sessions == null) {
            return "";
        }
        return sessions + (sessions == 1 ? " session" : " sessions");
    }

    private static int wholeSeconds(BigDecimal seconds) {
        return seconds.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Seconds as minutes and seconds: {@code 365} becomes {@code "6:05"}. */
    private static String clock(int seconds) {
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /** A lower number of seconds a kilometre is a faster pace. */
    private static String paceChangeNote(int current, int earlier) {
        int diff = earlier - current;
        if (diff == 0) {
            return "Unchanged";
        }
        return Math.abs(diff) + " s " + (diff > 0 ? "faster" : "slower");
    }

    /**
     * At most two decimal places, with trailing zeros dropped: {@code 5.750} becomes {@code "5.75"}.
     * Package-private so {@link RuleReadingWriter} formats the earlier distance the way the fact does.
     */
    static String upToTwoDecimals(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0);
        }
        return scaled.toPlainString();
    }

    private void addVolumePerSession(List<ReadingFact> facts, TrainingInsight insight) {
        BigDecimal volume = insight.volumePerSessionKg();
        if (volume == null) {
            return;
        }
        BigDecimal earlier = insight.earlierVolumePerSessionKg();
        String detail = earlier == null ? "" : wholeWithCommas(earlier) + " kg the week before";
        String note = earlier == null ? "" : percentChangeNote(volume, earlier);
        facts.add(new ReadingFact(UNNUMBERED, "VOLUME_PER_SESSION", "Weight moved per session",
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
        facts.add(new ReadingFact(UNNUMBERED, "SESSION_LENGTH", "Session length",
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
        facts.add(new ReadingFact(UNNUMBERED, "RECORD", exerciseName + " record",
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
        facts.add(new ReadingFact(UNNUMBERED, "MOST_TRAINED", "Sessions with " + muscleLabel + " work",
                String.valueOf(mostTrained.sessions()), "of " + sessionsInPeriod,
                mostTrained.sessions() == null ? null : BigDecimal.valueOf(mostTrained.sessions()), note, detail));
    }

    /** The change against the earlier value as a whole percent, shared with the other pages' builders. */
    static String percentChangeNote(BigDecimal current, BigDecimal earlier) {
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

    /** The chosen facts numbered from one in order, the numbering every page's facts share. */
    static List<ReadingFact> numbered(List<ReadingFact> chosen) {
        List<ReadingFact> numbered = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            ReadingFact fact = chosen.get(i);
            numbered.add(new ReadingFact(i + 1, fact.key(), fact.label(), fact.value(), fact.unit(),
                    fact.numericValue(), fact.note(), fact.detail(), fact.card()));
        }
        return List.copyOf(numbered);
    }
}
