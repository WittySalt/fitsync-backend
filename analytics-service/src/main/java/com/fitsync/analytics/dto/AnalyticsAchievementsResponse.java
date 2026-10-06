package com.fitsync.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The badge catalogue for one user (Documents/architecture/me-achievements-spec.md, section 2): seventeen
 * badges in four themes, each theme one or two paths of badges in order. A badge carries numbers only; the
 * client writes the gap sentences.
 *
 * @param earned  how many of the catalogue's badges are earned
 * @param total   how many badges the catalogue has
 * @param themes  the four themes in display order
 * @param latest  the most recently earned catalogue badge, null when none is earned
 * @param nextUp  the two closest unearned badges, each the next badge of its own path
 * @param earlier retired badges the user earned before they were retired, oldest first
 * @param pace    how often the user trains, for the client's "at your pace" sentence
 */
public record AnalyticsAchievementsResponse(
        int earned,
        int total,
        List<Theme> themes,
        Badge latest,
        List<Badge> nextUp,
        List<Badge> earlier,
        Pace pace) {

    public AnalyticsAchievementsResponse {
        themes = List.copyOf(themes);
        nextUp = List.copyOf(nextUp);
        earlier = List.copyOf(earlier);
    }

    @Override
    public List<Theme> themes() {
        return List.copyOf(themes);
    }

    @Override
    public List<Badge> nextUp() {
        return List.copyOf(nextUp);
    }

    @Override
    public List<Badge> earlier() {
        return List.copyOf(earlier);
    }

    public record Theme(String code, String name, List<Path> paths) {

        public Theme {
            paths = List.copyOf(paths);
        }

        @Override
        public List<Path> paths() {
            return List.copyOf(paths);
        }
    }

    public record Path(String code, List<Badge> nodes) {

        public Path {
            nodes = List.copyOf(nodes);
        }

        @Override
        public List<Badge> nodes() {
            return List.copyOf(nodes);
        }
    }

    /**
     * One badge.
     *
     * @param state    EARNED, NEXT (the first unearned badge of its path) or LATER
     * @param earnedOn the day the badge was first earned, null while it is not
     * @param progress how far along the badge is, null when analytics holds no figure for it
     * @param evidence what earned it, null while it is not earned
     */
    public record Badge(
            String code,
            String theme,
            String name,
            String how,
            String state,
            LocalDate earnedOn,
            Progress progress,
            Evidence evidence) {
    }

    /** {@code current} of {@code target} in {@code unit}; ratio badges use decimals. */
    public record Progress(BigDecimal current, BigDecimal target, String unit) {
    }

    /**
     * What earned a badge. Every field is optional.
     *
     * @param weekStart  the Monday of the week that earned a week badge
     * @param monthStart the first day of the month that earned a month badge
     * @param sessionId  the session that earned a session-count badge, when analytics knows it
     * @param recordId   the record that earned a record badge; analytics holds no record ids, so null for now
     * @param weekDays   for a week badge, Monday to Sunday: whether the day counted (trained, or protein met)
     */
    public record Evidence(
            LocalDate weekStart,
            LocalDate monthStart,
            UUID sessionId,
            UUID recordId,
            List<Boolean> weekDays) {

        public Evidence {
            weekDays = weekDays == null ? null : List.copyOf(weekDays);
        }

        @Override
        public List<Boolean> weekDays() {
            return weekDays == null ? null : List.copyOf(weekDays);
        }
    }

    /**
     * @param sessionsPerWeek completed sessions a week over the last four full weeks, one decimal
     * @param weeks           how many full weeks the average covers
     */
    public record Pace(BigDecimal sessionsPerWeek, int weeks) {
    }
}
