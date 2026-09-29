package com.fitsync.recommendation.coach.orchestrator;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The session and minute counts of the two weeks the coach is given: the last full week, from the
 * computed facts, and the current week so far, from workout-service.
 *
 * <p>Read back out of the {@code readFacts} observation in the transcript, where both groups are
 * flat JSON objects. The checker uses them to tell a count that belongs to last week from one that
 * belongs to this week. Any of the four can be missing, and a rule that needs a missing one does
 * not run.
 */
record WeekFigures(Integer lastWeekSessions, Integer lastWeekMinutes,
                   Integer currentWeekSessions, Integer currentWeekMinutes) {

    private static final String FACTS_OBSERVATION = "Result of readFacts(";

    private static final Pattern LAST_WEEK_GROUP = Pattern.compile("\"training\"\\s*:\\s*\\{([^{}]*)}");
    private static final Pattern CURRENT_WEEK_GROUP = Pattern.compile("\"trainingThisWeek\"\\s*:\\s*\\{([^{}]*)}");

    static WeekFigures from(List<String> transcript) {
        if (transcript == null) {
            return new WeekFigures(null, null, null, null);
        }
        String facts = null;
        for (String entry : transcript) {
            if (entry != null && entry.startsWith(FACTS_OBSERVATION)) {
                facts = entry;
            }
        }
        if (facts == null) {
            return new WeekFigures(null, null, null, null);
        }
        String lastWeek = group(LAST_WEEK_GROUP, facts);
        String currentWeek = group(CURRENT_WEEK_GROUP, facts);
        return new WeekFigures(
                number(lastWeek, "workoutCount"), number(lastWeek, "totalMinutes"),
                number(currentWeek, "sessions"), number(currentWeek, "totalMinutes"));
    }

    /** True when both weeks are known, which is what telling them apart needs. */
    boolean comparable() {
        return currentWeekSessions != null && currentWeekMinutes != null
                && (lastWeekSessions != null || lastWeekMinutes != null);
    }

    private static String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Integer number(String group, String field) {
        if (group == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d{1,6})\\b").matcher(group);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
