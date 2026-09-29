package com.fitsync.recommendation.coach.reading;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Writes the reading a page shows by default: a small set of fixed templates over the same four
 * facts a model reading would be checked against, never a model call.
 *
 * <p>The product's own history is why this exists. A small model wrote readings that cited real
 * numbers correctly, used no banned words and still said something false, because "passes the
 * checks" and "is true" are not the same guarantee. A template chosen from the fact directions
 * cannot say anything the facts did not show, because there is no free text for it to say it in.
 *
 * <p>Pure and deterministic: no I/O, no clock, and the only collaborator is {@link ReadingValidator}
 * itself, used here for the same reason it is used on a model's answer. An exercise name comes from
 * a user and can contain digits, punctuation the validator treats as a dash, or characters outside
 * its printable-ASCII rule, so a sentence that quotes one is tried and, if it fails, dropped rather
 * than trusted blind.
 */
@Component
public class RuleReadingWriter {

    private static final String RECORD_HEADLINE = "You set a new personal best.";

    private static final int EXERCISE_NAME_MAX_LENGTH = 60;

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final Map<String, String> MUSCLE_GROUP_TITLES = Map.ofEntries(
            Map.entry("FULL_BODY", "Whole-body"),
            Map.entry("CHEST", "Chest"),
            Map.entry("BACK", "Back"),
            Map.entry("SHOULDERS", "Shoulder"),
            Map.entry("ARMS", "Arm"),
            Map.entry("CORE", "Core"),
            Map.entry("GLUTES", "Glute"),
            Map.entry("LEGS", "Leg"),
            Map.entry("CARDIO", "Cardio"));

    private final ReadingValidator validator;

    public RuleReadingWriter(ReadingValidator validator) {
        this.validator = validator;
    }

    /**
     * Builds a rule-written reading for the given facts, trying candidates from the fullest to the
     * plainest until one passes {@link ReadingValidator}, and returning nothing when none does or
     * when the facts leave nothing to say.
     */
    public Optional<RuleReading> write(TrainingInsight insight, List<ReadingFact> facts) {
        ReadingFact volumeFact = findFact(facts, "VOLUME_PER_SESSION");
        ReadingFact lengthFact = findFact(facts, "SESSION_LENGTH");
        ReadingFact recordFact = findFact(facts, "RECORD");
        ReadingFact mostTrainedFact = findFact(facts, "MOST_TRAINED");

        String sentenceOne = sentenceOne(volumeFact, lengthFact, insight);
        String sentenceTwo = sentenceTwo(recordFact, insight);
        String sentenceThree = sentenceThree(mostTrainedFact, insight);

        String baseHeadline = baseHeadline(volumeFact, lengthFact, recordFact, facts.size());

        for (Candidate candidate : Candidate.IN_ORDER) {
            StringBuilder body = new StringBuilder();
            if (candidate.includeOne() && sentenceOne != null) {
                appendSentence(body, sentenceOne);
            }
            if (candidate.includeTwo() && sentenceTwo != null) {
                appendSentence(body, sentenceTwo);
            }
            if (candidate.includeThree() && sentenceThree != null) {
                appendSentence(body, sentenceThree);
            }
            if (body.length() == 0) {
                continue;
            }

            boolean includesRecordSentence = candidate.includeTwo() && sentenceTwo != null;
            String headline = baseHeadline;
            if (RECORD_HEADLINE.equals(headline) && !includesRecordSentence) {
                headline = otherwiseHeadline(facts.size());
            }

            ReadingValidator.Result result = validator.validate(headline, body.toString(), facts);
            if (result.valid()) {
                return Optional.of(new RuleReading(headline, body.toString(), result.citations()));
            }
        }
        return Optional.empty();
    }

    private static void appendSentence(StringBuilder body, String sentence) {
        if (body.length() > 0) {
            body.append(' ');
        }
        body.append(sentence);
    }

    /**
     * The headline the fact directions call for, before any candidate-specific dropping of the
     * record sentence is taken into account.
     */
    private static String baseHeadline(ReadingFact volumeFact, ReadingFact lengthFact,
                                        ReadingFact recordFact, int factCount) {
        Direction volumeDirection = directionOf(volumeFact);
        Direction lengthDirection = directionOf(lengthFact);

        if (volumeDirection == Direction.UP && lengthDirection == Direction.UNCHANGED) {
            return "Your lifts grew while your sessions stayed the same length.";
        }
        if (volumeDirection == Direction.UP && lengthDirection == Direction.DOWN) {
            return "You moved more weight in shorter sessions.";
        }
        if (volumeDirection == Direction.UP && lengthDirection == Direction.UP) {
            return "Your sessions grew longer and moved more weight.";
        }
        if (volumeDirection == Direction.UP) {
            return "You moved more weight a session than the week before.";
        }
        if (volumeDirection == Direction.DOWN && lengthDirection == Direction.DOWN) {
            return "Your sessions were shorter and moved less weight.";
        }
        if (volumeDirection == Direction.DOWN) {
            return "You moved less weight a session than the week before.";
        }
        if (volumeDirection == Direction.UNCHANGED) {
            return "Your sessions moved the same weight as the week before.";
        }
        if (recordFact != null) {
            return RECORD_HEADLINE;
        }
        return otherwiseHeadline(factCount);
    }

    private static String otherwiseHeadline(int factCount) {
        return "Your training in " + numberWord(factCount) + " numbers.";
    }

    private static String numberWord(int n) {
        return switch (n) {
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            default -> String.valueOf(n);
        };
    }

    private static String sentenceOne(ReadingFact volumeFact, ReadingFact lengthFact, TrainingInsight insight) {
        if (volumeFact != null && lengthFact != null) {
            String minutesClause = ", in " + lengthFact.value() + " min " + cite(lengthFact);
            return switch (directionOf(volumeFact)) {
                case UP -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", up from " + earlierVolume(insight) + " kg" + minutesClause + ".";
                case DOWN -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", down from " + earlierVolume(insight) + " kg" + minutesClause + ".";
                case UNCHANGED -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", the same as the week before" + minutesClause + ".";
                case NONE -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + " in " + lengthFact.value() + " min " + cite(lengthFact) + ".";
            };
        }
        if (volumeFact != null) {
            return switch (directionOf(volumeFact)) {
                case UP -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", up from " + earlierVolume(insight) + " kg.";
                case DOWN -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", down from " + earlierVolume(insight) + " kg.";
                case UNCHANGED -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact)
                        + ", the same as the week before.";
                case NONE -> "You moved " + volumeFact.value() + " kg a session " + cite(volumeFact) + ".";
            };
        }
        if (lengthFact != null) {
            return "Your sessions took " + lengthFact.value() + " min each " + cite(lengthFact) + ".";
        }
        return null;
    }

    private static String earlierVolume(TrainingInsight insight) {
        return ReadingFactsBuilder.wholeWithCommas(insight.earlierVolumePerSessionKg());
    }

    /**
     * The record sentence, or nothing when there is no record fact or no earlier best to measure it
     * against — the prompt gives no wording for a record with nothing to compare it to, so that case
     * is left unsaid rather than guessed at.
     */
    private static String sentenceTwo(ReadingFact recordFact, TrainingInsight insight) {
        if (recordFact == null) {
            return null;
        }
        TrainingInsight.RecordInfo record = insight.personalRecord();
        if (record == null || record.previousBestKg() == null) {
            return null;
        }
        String weekday = recordFact.note() == null ? "" : recordFact.note();
        String lead = weekday.isEmpty() ? "This period" : weekday;
        String exercise = trimAndCutExerciseName(record.exerciseName());
        String previous = ReadingFactsBuilder.upToOneDecimal(record.previousBestKg());
        return lead + " brought a " + exercise + " of " + recordFact.value() + " kg " + cite(recordFact)
                + ", up from " + previous + " kg.";
    }

    private static String trimAndCutExerciseName(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() > EXERCISE_NAME_MAX_LENGTH ? trimmed.substring(0, EXERCISE_NAME_MAX_LENGTH) : trimmed;
    }

    private static String sentenceThree(ReadingFact mostTrainedFact, TrainingInsight insight) {
        if (mostTrainedFact == null) {
            return null;
        }
        TrainingInsight.MostTrainedInfo mostTrained = insight.mostTrained();
        if (mostTrained == null) {
            return null;
        }
        String group = MUSCLE_GROUP_TITLES.get(mostTrained.muscleGroup());
        if (group == null) {
            return null;
        }
        String sessionsInPeriod = numberInsideUnit(mostTrainedFact.unit());
        if (sessionsInPeriod == null) {
            return null;
        }
        return group + " work was part of " + mostTrainedFact.value() + " of " + sessionsInPeriod
                + " sessions " + cite(mostTrainedFact) + ".";
    }

    private static String numberInsideUnit(String unit) {
        if (unit == null) {
            return null;
        }
        Matcher matcher = DIGITS.matcher(unit);
        return matcher.find() ? matcher.group() : null;
    }

    private static String cite(ReadingFact fact) {
        return "[" + fact.number() + "]";
    }

    private static ReadingFact findFact(List<ReadingFact> facts, String key) {
        for (ReadingFact fact : facts) {
            if (key.equals(fact.key())) {
                return fact;
            }
        }
        return null;
    }

    /**
     * A fact's direction as its note states it: {@code Up...} and {@code Down...} are prefixes
     * because a percentage or a minute count follows them, {@code Unchanged} is the whole note, and
     * an absent fact or an empty note (no earlier value to compare against) both read as
     * {@link #NONE}.
     */
    private static Direction directionOf(ReadingFact fact) {
        if (fact == null) {
            return Direction.NONE;
        }
        String note = fact.note() == null ? "" : fact.note();
        if (note.startsWith("Up")) {
            return Direction.UP;
        }
        if (note.startsWith("Down")) {
            return Direction.DOWN;
        }
        if ("Unchanged".equals(note)) {
            return Direction.UNCHANGED;
        }
        return Direction.NONE;
    }

    private enum Direction { UP, DOWN, UNCHANGED, NONE }

    /**
     * One combination of sentences to try as the body, tried in order from the fullest reading to
     * the plainest one that still says something, so the writer keeps as much as it can validate.
     */
    private record Candidate(boolean includeOne, boolean includeTwo, boolean includeThree) {

        static final List<Candidate> IN_ORDER = List.of(
                new Candidate(true, true, true),
                new Candidate(true, false, true),
                new Candidate(true, false, false),
                new Candidate(false, false, true));
    }

    /** A reading written from fixed templates rather than a model, already citation-checked. */
    public record RuleReading(String headline, String body, List<Integer> citations) {

        public RuleReading {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }

        @Override
        public List<Integer> citations() {
            return List.copyOf(citations);
        }
    }
}
