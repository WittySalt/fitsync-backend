package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;

/** The few ways numbers and dates are written in progress copy, matching the boards. */
public final class ProgressText {

    /** The minus sign the boards use in front of a negative change. */
    public static final String MINUS = "−";

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "June", "July", "Aug", "Sept", "Oct",
        "Nov", "Dec"};
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve"};
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private ProgressText() {
    }

    /** "8 Sept", "2 Oct", "12 July". */
    public static String date(LocalDate date) {
        return date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
    }

    /** "1 h 10 min", "2 h", "45 min". */
    public static String hm(long minutes) {
        long abs = Math.abs(minutes);
        long hours = abs / 60;
        long rest = abs % 60;
        if (hours > 0 && rest > 0) {
            return hours + " h " + rest + " min";
        }
        if (hours > 0) {
            return hours + " h";
        }
        return rest + " min";
    }

    /** "+1 h 10 min" or "−10 min". */
    public static String signedHm(long minutes) {
        return (minutes < 0 ? MINUS : "+") + hm(minutes);
    }

    /** "+14%" or "−3%". */
    public static String signedPct(int pct) {
        return (pct < 0 ? MINUS : "+") + Math.abs(pct) + "%";
    }

    /** One decimal, dropping ".0": "93.3", "80". */
    public static String kg(BigDecimal value) {
        String text = value.setScale(1, RoundingMode.HALF_UP).toPlainString();
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** "+11.6 kg" or "−2.5 kg", one decimal kept. */
    public static String signedKg(BigDecimal value) {
        BigDecimal scaled = value.setScale(1, RoundingMode.HALF_UP);
        return (scaled.signum() < 0 ? MINUS : "+") + scaled.abs().toPlainString() + " kg";
    }

    /** "5:50". */
    public static String pace(int secondsPerKm) {
        return secondsPerKm / 60 + ":" + String.format(Locale.ROOT, "%02d", secondsPerKm % 60);
    }

    /** "1,175". */
    public static String thousands(long value) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }

    /** A weekly weight volume: "1.9 t" from a tonne, "850 kg" below it. */
    public static String volume(BigDecimal kg) {
        if (kg.compareTo(THOUSAND) >= 0) {
            return kg.divide(THOUSAND, 1, RoundingMode.HALF_UP).toPlainString() + " t";
        }
        return thousands(kg.setScale(0, RoundingMode.HALF_UP).longValue()) + " kg";
    }

    /** "one" to "twelve", then digits. */
    public static String word(long n) {
        return n >= 0 && n < WORDS.length ? WORDS[(int) n] : Long.toString(n);
    }

    /** The period in a title: "a week", "four weeks", "three months", "a year", or "45 days". */
    public static String periodPhrase(long days) {
        if (days == 7) {
            return "a week";
        }
        if (days % 7 == 0 && days / 7 <= 8) {
            return word(days / 7) + " weeks";
        }
        if (days >= 84 && days <= 93) {
            return "three months";
        }
        if (days >= 360) {
            return "a year";
        }
        return days == 1 ? "a day" : days + " days";
    }

    /** The previous period in context lines: "the 4 weeks before", "the year before", "the 10 days before". */
    public static String previousLabel(long days) {
        if (days == 7) {
            return "the week before";
        }
        if (days % 7 == 0 && days / 7 <= 8) {
            return "the " + days / 7 + " weeks before";
        }
        if (days >= 84 && days <= 93) {
            return "the 3 months before";
        }
        if (days >= 360) {
            return "the year before";
        }
        return days == 1 ? "the day before" : "the " + days + " days before";
    }

    /** "1 session", "3 sessions". */
    public static String sessions(long count) {
        return count + (count == 1 ? " session" : " sessions");
    }
}
