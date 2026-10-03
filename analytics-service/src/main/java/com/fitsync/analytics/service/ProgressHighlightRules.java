package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.dto.ProgressHighlight.Point;

/**
 * The fixed rules behind the Goal and Food highlights (contract sections 2.1 and 2.2). Each rule returns the
 * finding or null when it does not fire; nothing here reads a repository or a clock.
 */
final class ProgressHighlightRules {

    static final String RATE_IN_RANGE = "RATE_IN_RANGE";
    static final String LOG_VS_SCALE = "LOG_VS_SCALE";
    static final String WAIST_VS_WEIGHT = "WAIST_VS_WEIGHT";
    static final String PROTEIN_MET = "PROTEIN_MET";
    static final String PROTEIN_DRIFT = "PROTEIN_DRIFT";
    static final String PLATE_SHIFT = "PLATE_SHIFT";
    static final String LARGEST_MEAL = "LARGEST_MEAL";

    /** The weekly loss that keeps muscle, in kg a week. */
    static final BigDecimal RATE_LOW = new BigDecimal("0.25");
    static final BigDecimal RATE_HIGH = new BigDecimal("0.50");
    /** A waist change smaller than this is tape noise. */
    static final BigDecimal WAIST_MIN_CHANGE_CM = new BigDecimal("0.5");
    static final int PROTEIN_MIN_LOGGED_DAYS = 3;
    /** Within 8% of the first week reads as steady, as on the macro summary lines. */
    static final BigDecimal DRIFT_SHARE = new BigDecimal("0.08");
    static final BigDecimal PLATE_MIN_POINTS = new BigDecimal("3");
    static final int LARGEST_MEAL_MIN_PCT = 35;

    private static final String[] MONTHS =
            {"Jan", "Feb", "Mar", "Apr", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
    private static final char MINUS = '−';

    private ProgressHighlightRules() {
    }

    /** Fires when the weight is going down toward a lower goal (or with no goal) at 0.25 to 0.5 kg a week. */
    static ProgressHighlight rateInRange(BigDecimal weeklyRate, BigDecimal previousRate, BigDecimal nowKg,
                                         BigDecimal goalKg) {
        if (weeklyRate == null || weeklyRate.signum() >= 0) {
            return null;
        }
        if (goalKg != null && nowKg != null && goalKg.compareTo(nowKg) >= 0) {
            return null;
        }
        BigDecimal loss = weeklyRate.negate();
        if (loss.compareTo(RATE_LOW) < 0 || loss.compareTo(RATE_HIGH) > 0) {
            return null;
        }
        String context = previousRate == null
                ? "The green band is the range."
                : "Was " + plain(previousRate.negate(), 2) + " kg a week in the period before. The green band is the range.";
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("weeklyRateKg", weeklyRate);
        inputs.put("previousRateKg", previousRate);
        inputs.put("low", RATE_LOW);
        inputs.put("high", RATE_HIGH);
        return new ProgressHighlight(RATE_IN_RANGE,
                "You lose " + plain(loss, 2) + " kg a week, inside the 0.25 to 0.5 kg range that keeps muscle.",
                List.of(new Point("Your rate", loss.doubleValue()), new Point("Low", RATE_LOW.doubleValue()),
                        new Point("High", RATE_HIGH.doubleValue())),
                "kg a week", context, inputs);
    }

    /** Fires when the energy view has both an expected and an actual change for the same week. */
    static ProgressHighlight logVsScale(LocalDate firstWeek, LocalDate lastWeek, int weeks, Double expectedKg,
                                        Double actualKg) {
        if (expectedKg == null || actualKg == null || firstWeek == null || weeks < 1) {
            return null;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("expectedKg", expectedKg);
        inputs.put("actualKg", actualKg);
        inputs.put("weeks", weeks);
        inputs.put("from", firstWeek);
        inputs.put("to", lastWeek.plusDays(6));
        return new ProgressHighlight(LOG_VS_SCALE,
                "The log says " + signed(expectedKg, 1) + " kg, the scale says " + signed(actualKg, 1)
                        + " kg over " + weeks + (weeks == 1 ? " week." : " weeks."),
                List.of(new Point("Food log", expectedKg), new Point("Scale", actualKg)),
                "kg", "From " + day(firstWeek) + " to " + day(lastWeek.plusDays(6))
                        + ". Days without a food log are not counted.", inputs);
    }

    /** Fires when the waist came down by at least half a centimetre and the weekly weight is known twice. */
    static ProgressHighlight waistVsWeight(List<LocalDate> waistDates, List<BigDecimal> waist,
                                           BigDecimal weightChangeKg) {
        if (waist.size() < 2 || weightChangeKg == null) {
            return null;
        }
        BigDecimal change = waist.get(waist.size() - 1).subtract(waist.get(0));
        if (change.negate().compareTo(WAIST_MIN_CHANGE_CM) < 0) {
            return null;
        }
        String weightWords = weightChangeKg.signum() <= 0
                ? "weight is down " + plain(weightChangeKg.abs(), 1) + " kg"
                : "weight is up " + plain(weightChangeKg, 1) + " kg";
        List<Point> series = new ArrayList<>();
        for (int i = 0; i < waist.size(); i++) {
            series.add(new Point(waistDates.get(i).toString(), waist.get(i).doubleValue()));
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("waistChangeCm", change);
        inputs.put("weightChangeKg", weightChangeKg);
        inputs.put("since", waistDates.get(0));
        return new ProgressHighlight(WAIST_VS_WEIGHT,
                "Waist is down " + plain(change.abs(), 1) + " cm while " + weightWords + ".",
                series, "cm", "Since " + day(waistDates.get(0)) + ", tape measures and weekly average weight",
                inputs);
    }

    /** Fires when a protein target is known on at least three logged days. */
    static ProgressHighlight proteinMet(List<LocalDate> loggedDates, List<BigDecimal> protein, int metDays,
                                        BigDecimal target) {
        if (target == null || loggedDates.size() < PROTEIN_MIN_LOGGED_DAYS) {
            return null;
        }
        List<Point> series = new ArrayList<>();
        for (int i = 0; i < loggedDates.size(); i++) {
            series.add(new Point(loggedDates.get(i).toString(), protein.get(i).doubleValue()));
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("metDays", metDays);
        inputs.put("loggedDays", loggedDates.size());
        inputs.put("targetGrams", target);
        return new ProgressHighlight(PROTEIN_MET,
                "Protein was met on " + metDays + " of " + loggedDates.size() + " logged days.",
                series, "g", "At least " + plain(target, 0) + " g counts as met. Days not logged are left out.",
                inputs);
    }

    /** Fires when the weekly protein average moved at least 8% between the first and the last week. */
    static ProgressHighlight proteinDrift(List<LocalDate> weeks, List<BigDecimal> values, BigDecimal target) {
        if (values.size() < 2) {
            return null;
        }
        BigDecimal first = values.get(0);
        BigDecimal last = values.get(values.size() - 1);
        if (first.signum() <= 0) {
            return null;
        }
        if (drift(first, last) == 0) {
            return null;
        }
        BigDecimal change = last.subtract(first).divide(first, 4, RoundingMode.HALF_UP);
        List<Point> series = new ArrayList<>();
        for (int i = 0; i < weeks.size(); i++) {
            series.add(new Point(weeks.get(i).toString(), values.get(i).doubleValue()));
        }
        String context = signed(last.subtract(first).doubleValue(), 0) + " g a day since the week of "
                + day(weeks.get(0)) + "." + (target == null ? "" : " The target is " + plain(target, 0) + " g.");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("firstWeek", first);
        inputs.put("thisWeek", last);
        inputs.put("changePct", change.movePointRight(2).setScale(1, RoundingMode.HALF_UP));
        inputs.put("targetGrams", target);
        return new ProgressHighlight(PROTEIN_DRIFT,
                "Protein drifted from " + plain(first, 0) + " g to " + plain(last, 0) + " g a day.",
                series, "g", context, inputs);
    }

    /** Fires when one food group's share of the plate moved at least three points from the period before. */
    static ProgressHighlight plateShift(String group, BigDecimal previousShare, BigDecimal currentShare) {
        if (group == null || previousShare == null || currentShare == null) {
            return null;
        }
        BigDecimal delta = currentShare.subtract(previousShare);
        if (delta.abs().compareTo(PLATE_MIN_POINTS) < 0) {
            return null;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("group", group);
        inputs.put("previousShare", previousShare);
        inputs.put("currentShare", currentShare);
        inputs.put("deltaPoints", delta);
        return new ProgressHighlight(PLATE_SHIFT,
                groupName(group) + (groupName(group).endsWith("s") ? " are a " : " is a ")
                        + (delta.signum() > 0 ? "bigger" : "smaller") + " share of your plate than in the period before.",
                List.of(new Point("Before", previousShare.doubleValue()), new Point("Now", currentShare.doubleValue())),
                "% of the plate", "By calories, the period before against this one", inputs);
    }

    /** Fires when one meal type holds at least 35% of the day's calories. */
    static ProgressHighlight largestMeal(String mealType, int sharePct, int kcalPerDay, int eatenPerDay,
                                         List<Point> shares) {
        if (mealType == null || sharePct < LARGEST_MEAL_MIN_PCT) {
            return null;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("mealType", mealType);
        inputs.put("sharePct", sharePct);
        inputs.put("kcalPerDay", kcalPerDay);
        inputs.put("eatenPerDay", eatenPerDay);
        return new ProgressHighlight(LARGEST_MEAL,
                mealName(mealType) + " is your largest meal, " + sharePct + "% of the day.",
                shares, "%", String.format(Locale.ROOT, "%,d of %,d kcal a day on logged days", kcalPerDay, eatenPerDay),
                inputs);
    }

    /** 1 when {@code last} is at least 8% above {@code first}, -1 when at least 8% below, else 0. */
    static int drift(BigDecimal first, BigDecimal last) {
        if (first == null || last == null || first.signum() <= 0) {
            return 0;
        }
        BigDecimal change = last.subtract(first);
        if (change.abs().compareTo(first.multiply(DRIFT_SHARE)) < 0) {
            return 0;
        }
        return change.signum();
    }

    static String groupName(String group) {
        return switch (group) {
            case "VEG" -> "Vegetables";
            case "FRUIT" -> "Fruit";
            case "GRAINS" -> "Grains";
            case "PROTEIN" -> "Protein foods";
            case "DAIRY" -> "Dairy";
            case "NUTS" -> "Nuts and seeds";
            case "LEGUMES" -> "Legumes";
            case "SWEETS" -> "Sweets";
            default -> "Other foods";
        };
    }

    static String mealName(String mealType) {
        String lower = mealType.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** "14 Sept". */
    static String day(LocalDate date) {
        return date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
    }

    /** A plain number with a fixed number of decimals, no sign added. */
    static String plain(BigDecimal value, int digits) {
        return value.setScale(digits, RoundingMode.HALF_UP).toPlainString().replace('-', MINUS);
    }

    /** "+0.4" or "−2.7" with a true minus sign. */
    static String signed(double value, int digits) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP);
        String text = rounded.abs().toPlainString();
        return (rounded.signum() < 0 ? MINUS + text : "+" + text);
    }
}
