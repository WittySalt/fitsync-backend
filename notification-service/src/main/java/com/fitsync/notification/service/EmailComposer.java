package com.fitsync.notification.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fitsync.notification.config.NotificationProperties;
import com.fitsync.notification.entity.PlanDayEntity;
import com.fitsync.notification.entity.RecipientEntity;

/**
 * Writes the subject and body of every email. The words follow the spec's tone rule: facts the
 * user can already see in the app, short and plain, with no pressure, no "missed" and no streaks.
 * Every body ends with the reason it was sent and a link to the notification settings.
 */
@Component
public class EmailComposer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final String appBaseUrl;

    public EmailComposer(NotificationProperties properties) {
        this.appBaseUrl = properties.appBaseUrl();
    }

    /** E1, today's training. Parts the plan does not know (minutes, a training time) are left out. */
    public ComposedEmail trainingToday(RecipientEntity recipient, PlanDayEntity day, LocalTime trainingTime) {
        String title = oneLine(day.getTitle());
        StringBuilder subject = new StringBuilder("Today: ").append(title);
        if (day.getEstimatedMinutes() != null) {
            subject.append(" · ").append(day.getEstimatedMinutes()).append(" min");
        }
        if (trainingTime != null) {
            subject.append(" at ").append(TIME.format(trainingTime));
        }

        StringBuilder body = new StringBuilder(greeting(recipient));
        body.append("Your plan has a session today.\n\n");
        body.append("Session: ").append(title).append('\n');
        if (day.getEstimatedMinutes() != null) {
            body.append("Time: about ").append(day.getEstimatedMinutes()).append(" min\n");
        }
        body.append("Exercises: ").append(day.getExerciseCount()).append('\n');
        if (trainingTime != null) {
            body.append("Starts: ").append(TIME.format(trainingTime)).append('\n');
        }
        body.append("\nOpen today's session:\n").append(appBaseUrl).append("/workout\n");
        return new ComposedEmail(subject.toString(), footer(body, "Workout reminders are on"));
    }

    /** E4, a weight goal reached. */
    public ComposedEmail goalReached(RecipientEntity recipient, BigDecimal target, BigDecimal startWeight,
                                     BigDecimal currentWeight, long days) {
        String subject = "Weight goal reached: " + kg(target);
        StringBuilder body = new StringBuilder(greeting(recipient));
        body.append("You reached your weight goal of ").append(kg(target)).append(".\n\n");
        body.append("Start: ").append(kg(startWeight)).append('\n');
        body.append("Now: ").append(kg(currentWeight)).append('\n');
        body.append("Days since the goal was set: ").append(days).append('\n');
        body.append("\nSet your next goal:\n").append(appBaseUrl).append("/me\n");
        return new ComposedEmail(subject, footer(body, "Goal progress reminders are on"));
    }

    /** E5, last week in numbers. Lines without data are left out rather than shown as zero. */
    public ComposedEmail weeklyReport(RecipientEntity recipient, WeeklyReportFacts facts) {
        String range = SHORT_DATE.format(facts.weekStart()) + " to " + SHORT_DATE.format(facts.weekEnd());
        String subject = "Your week, " + range;
        StringBuilder body = new StringBuilder(greeting(recipient));
        body.append("Here is last week, ").append(range).append(".\n\n");
        body.append("Workouts: ").append(facts.workoutCount());
        if (facts.weeklyWorkoutGoal() != null) {
            body.append(" (weekly goal: ").append(number(facts.weeklyWorkoutGoal())).append(')');
        }
        body.append('\n');
        body.append("Training time: ").append(facts.workoutMinutes()).append(" min\n");
        body.append("Active days: ").append(facts.activeDays()).append(" of 7\n");
        if (facts.caloriesConsumed() > 0) {
            // Analytics gives a weekly total without the number of days logged, so an average
            // would be a guess; the total is stated as a total.
            body.append("Calories logged: ")
                    .append(String.format(Locale.ENGLISH, "%,d", facts.caloriesConsumed()))
                    .append(" kcal in total\n");
        }
        if (facts.weightBefore() != null && facts.weightAfter() != null) {
            BigDecimal change = facts.weightAfter().subtract(facts.weightBefore());
            String sign = change.signum() > 0 ? "+" : "";
            body.append("Weight: ").append(kg(facts.weightBefore())).append(" to ")
                    .append(kg(facts.weightAfter())).append(" (").append(sign).append(kg(change)).append(")\n");
        }
        body.append("\nSee your progress:\n").append(appBaseUrl).append("/progress\n");
        return new ComposedEmail(subject, footer(body, "the Weekly progress report is on"));
    }

    /** E6, the account is scheduled for deletion. Always sent, so its reason is the account's safety. */
    public ComposedEmail deletionScheduled(RecipientEntity recipient, Instant scheduledFor, ZoneId zone) {
        String login = appBaseUrl + "/auth/login";
        StringBuilder body = new StringBuilder(greeting(recipient));
        body.append("Your FitSync account is scheduled for deletion");
        if (scheduledFor != null) {
            body.append(" on ").append(LONG_DATE.format(scheduledFor.atZone(zone)));
        }
        body.append(".\n\n");
        body.append("To keep it, sign in before then to cancel:\n").append(login).append("\n\n");
        body.append("If this was not you, reset your password:\n").append(login).append('\n');
        return new ComposedEmail("Your account is scheduled for deletion",
                footer(body, "it is a security notice about your account"));
    }

    private String footer(StringBuilder body, String reason) {
        body.append("\n--\n");
        body.append("You get this because ").append(reason).append(".\n");
        body.append("Email settings:\n").append(appBaseUrl).append("/settings?section=notifications\n");
        return body.toString();
    }

    private static String greeting(RecipientEntity recipient) {
        String name = recipient == null ? null : recipient.getDisplayName();
        if (name == null || name.isBlank()) {
            return "Hi,\n\n";
        }
        return "Hi " + oneLine(name) + ",\n\n";
    }

    /** Keeps a user-typed value on one line, so it cannot add lines to the subject or the body. */
    private static String oneLine(String value) {
        return value.replaceAll("[\\r\\n]+", " ").strip();
    }

    private static String kg(BigDecimal value) {
        return number(value) + " kg";
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * The facts of one weekly report.
     *
     * @param weeklyWorkoutGoal the active weekly session goal, or null without one
     * @param weightBefore      the last weight before the week, or null
     * @param weightAfter       the last weight inside the week, or null
     */
    public record WeeklyReportFacts(LocalDate weekStart, LocalDate weekEnd, int workoutCount, int workoutMinutes,
                                    int activeDays, int caloriesConsumed, BigDecimal weeklyWorkoutGoal,
                                    BigDecimal weightBefore, BigDecimal weightAfter) {
    }

    /** A subject and a plain-text body. */
    public record ComposedEmail(String subject, String bodyText) {
    }
}
