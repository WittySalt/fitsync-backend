package com.fitsync.nutrition.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pulls the brand out of a USDA food name. SR Legacy writes the restaurant or maker in front of the name, either
 * before the first comma ("McDONALD'S, BIG MAC") or, for a few makers, before the first space ("PIZZA HUT 14"
 * Cheese Pizza, ..."). Only a fixed list of names counts as a brand, so a food that merely starts with a capital
 * word is left alone. The rest of the name is written in title case.
 */
public final class UsdaBrandRules {

    /** A brand as the catalogue stores it, and whether its name may also be followed by a space instead of a comma. */
    private record Brand(String name, boolean spaceForm) {
    }

    /** The chains and makers whose foods SR Legacy names with the brand in front, longest name first. */
    private static final List<Brand> BRANDS = brands();

    /** Spellings in the data that stand for a listed brand. */
    private static final Map<String, String> ALIASES = Map.of(
            "KRAFT FOODS", "KRAFT",
            "WEND'YS", "WENDY'S",
            "CAMPBELL", "CAMPBELL'S");

    /** Words that stay in capitals, such as the "BBQ" in "BBQ SAUCE". */
    private static final Set<String> ACRONYMS = Set.of(
            "BBQ", "BLT", "USDA", "USA", "DHA", "UHT", "RTE", "XL", "II", "III", "PB", "OJ");

    /** Small words that stay in lower case in the middle of a title, when the source wrote them in capitals. */
    private static final Set<String> SMALL_WORDS = Set.of(
            "A", "AN", "AND", "OR", "OF", "WITH", "WITHOUT", "IN", "ON", "THE", "FOR", "TO", "FROM", "AT", "PER");

    private static final Pattern WITH_SLASH = Pattern.compile("(?i)(^|[\\s(])w/");

    /** The brand and the rewritten name of one food. */
    public record Rewrite(String brandName, String displayName) {
    }

    private UsdaBrandRules() {
    }

    /** The names a food may be branded with, as stored. */
    public static List<String> brandNames() {
        return BRANDS.stream().map(Brand::name).toList();
    }

    /** The brand and the title-cased rest of the name, or empty when the name does not start with a listed brand. */
    public static Optional<Rewrite> rewrite(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        int comma = trimmed.indexOf(',');
        if (comma > 0) {
            Optional<Brand> byComma = find(trimmed.substring(0, comma).trim());
            if (byComma.isPresent()) {
                return finish(byComma.get(), trimmed.substring(comma + 1));
            }
        }
        String upper = upper(trimmed);
        for (Brand brand : BRANDS) {
            String prefix = upper(brand.name()) + " ";
            if (brand.spaceForm() && upper.startsWith(prefix)) {
                return finish(brand, trimmed.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Brand> find(String head) {
        String key = upper(head);
        String target = ALIASES.getOrDefault(key, key);
        return BRANDS.stream().filter(b -> upper(b.name()).equals(target)).findFirst();
    }

    private static Optional<Rewrite> finish(Brand brand, String rest) {
        String remainder = rest.trim();
        if (remainder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Rewrite(brand.name(), titleCase(remainder)));
    }

    /**
     * Title case for the USDA's capitals. A word written in capitals becomes "Capitalised" unless it is a known
     * acronym or a small word, a single capital letter stays as it is, and a word that already has lower case
     * letters is kept, except that "McNUGGETS" becomes "McNuggets". "w/" is written "with". The first letter is
     * always a capital.
     */
    public static String titleCase(String text) {
        String source = WITH_SLASH.matcher(text).replaceAll("$1with");
        StringBuilder out = new StringBuilder(source.length());
        boolean first = true;
        int i = 0;
        while (i < source.length()) {
            if (!Character.isLetter(source.charAt(i))) {
                out.append(source.charAt(i));
                i++;
                continue;
            }
            int end = i + 1;
            while (end < source.length() && continuesWord(source, end)) {
                end++;
            }
            out.append(word(source.substring(i, end), first));
            first = false;
            i = end;
        }
        // A name that starts in lower case ("biscuit") starts with a capital as the rest of the catalogue does.
        if (out.length() > 0 && out.charAt(0) >= 'a' && out.charAt(0) <= 'z') {
            out.setCharAt(0, (char) (out.charAt(0) - ('a' - 'A')));
        }
        return out.toString();
    }

    /** A letter continues a word, and so does an apostrophe between two letters ("BREAKSTONE'S"). */
    private static boolean continuesWord(String text, int index) {
        char c = text.charAt(index);
        if (Character.isLetter(c)) {
            return true;
        }
        return c == '\'' && index + 1 < text.length() && Character.isLetter(text.charAt(index + 1));
    }

    private static String word(String word, boolean first) {
        String upper = upper(word);
        if (word.equals(upper)) {
            if (ACRONYMS.contains(upper)) {
                return word;
            }
            if (SMALL_WORDS.contains(upper) && !first) {
                return lower(word);
            }
            return word.length() == 1 ? word : capitalise(word);
        }
        String tail = word.length() > 3 ? word.substring(2) : "";
        if (word.startsWith("Mc") && tail.equals(upper(tail))) {
            return "Mc" + capitalise(tail);
        }
        return word;
    }

    private static String capitalise(String word) {
        return upper(word.substring(0, 1)) + lower(word.substring(1));
    }

    /** Upper case for the letters a-z only. The USDA names are ASCII, and a locale-aware case mapping is not wanted. */
    private static String upper(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
        }
        return out.toString();
    }

    private static String lower(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return out.toString();
    }

    private static List<Brand> brands() {
        List<Brand> list = new ArrayList<>();
        // The chains and makers named by the owner.
        for (String name : List.of("McDONALD'S", "WENDY'S", "BURGER KING", "KFC", "SUBWAY", "TACO BELL", "DENNY'S",
                "APPLEBEE'S", "CHICK-FIL-A", "STARBUCKS", "DUNKIN'", "CHILI'S", "OLIVE GARDEN", "CRACKER BARREL",
                "T.G.I. FRIDAY'S", "POPEYES", "ARBY'S", "SONIC", "JACK IN THE BOX", "CARL'S JR.", "PANERA",
                "KELLOGG'S", "GENERAL MILLS", "QUAKER", "POST", "NABISCO", "HEINZ", "NESTLE", "HERSHEY'S", "MARS",
                "PEPSICO", "COCA-COLA")) {
            list.add(new Brand(name, name.equals("McDONALD'S")));
        }
        // Chains and makers whose names SR Legacy writes in front of a space as well as before a comma.
        for (String name : List.of("DOMINO'S", "PIZZA HUT", "PAPA JOHN'S", "LITTLE CAESARS", "CAMPBELL'S", "KRAFT",
                "HOT POCKETS", "HORMEL", "HOUSE FOODS", "DIGIORNO", "SILK", "Vitasoy USA")) {
            list.add(new Brand(name, true));
        }
        // Other brands the SR Legacy data writes in capitals before the first comma.
        for (String name : List.of("SMART SOUP", "MORI-NU", "ON THE BORDER", "CARRABBA'S ITALIAN GRILL",
                "LEAN POCKETS", "SCHIFF", "HUNGRY MAN", "BANQUET", "JIMMY DEAN", "RICE-A-RONI")) {
            list.add(new Brand(name, false));
        }
        list.sort(Comparator.comparingInt((Brand b) -> b.name().length()).reversed());
        return List.copyOf(list);
    }
}
